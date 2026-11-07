package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMException;
import com.eecs3311.productfinder.domain.catalog.ProductSpecValidationException;
import com.eecs3311.productfinder.domain.chat.ChatSession;
import com.eecs3311.productfinder.domain.chat.ChatState;
import com.eecs3311.productfinder.domain.chat.LastMatch;
import com.eecs3311.productfinder.domain.chat.MessageRole;
import com.eecs3311.productfinder.domain.chat.Requirements;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.infrastructure.persistence.ListingRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AgentService {

    private final SessionStore sessionStore;
    private final ProfileService profileService;
    private final RequirementsExtractor requirementsExtractor;
    private final ProductMatcher productMatcher;
    private final PriceHistoryService priceHistoryService;
    private final BuyWaitAdvisor buyWaitAdvisor;
    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final SimilarProductSuggester similarProductSuggester;

    public AgentService(
            SessionStore sessionStore,
            ProfileService profileService,
            RequirementsExtractor requirementsExtractor,
            ProductMatcher productMatcher,
            PriceHistoryService priceHistoryService,
            BuyWaitAdvisor buyWaitAdvisor,
            ListingRepository listingRepository,
            ProductRepository productRepository,
            SimilarProductSuggester similarProductSuggester
    ) {
        this.sessionStore = sessionStore;
        this.profileService = profileService;
        this.requirementsExtractor = requirementsExtractor;
        this.productMatcher = productMatcher;
        this.priceHistoryService = priceHistoryService;
        this.buyWaitAdvisor = buyWaitAdvisor;
        this.listingRepository = listingRepository;
        this.productRepository = productRepository;
        this.similarProductSuggester = similarProductSuggester;
    }

    public Optional<Product> suggestSimilar(String productName) {
        return similarProductSuggester.suggest(productName);
    }

    public ChatFlowResponse handleMessage(long userId, String text) {
        if (text == null || text.isBlank() || text.length() > 4000) {
            throw new IllegalArgumentException("Message must contain 1 to 4000 characters");
        }
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            if (session.getState() != ChatState.GATHERING) {
                session.transitionTo(ChatState.GATHERING);
            }
            session.addMessage(MessageRole.USER, text.trim());
            sessionStore.save(session);

            RequirementsExtractionResult extraction = requirementsExtractor.extract(
                    session.getHistory(),
                    profileService.get(userId)
            );
            if (!extraction.isReady()) {
                session.addMessage(MessageRole.ASSISTANT, extraction.followUpQuestion());
                sessionStore.save(session);
                return response(session, null, extraction.followUpQuestion(), null, null, List.of());
            }
            Requirements requirements = extraction.requirements();
            session.setPendingRequirements(requirements);
            session.addMessage(MessageRole.ASSISTANT, confirmationText());
            session.transitionTo(ChatState.CONFIRMING);
            sessionStore.save(session);
            return response(session, requirements, null, null, null, List.of());
        }
    }

    public ChatFlowResponse current(long userId) {
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            ProductMatch currentMatch = loadLastMatch(session);
            return response(
                    session,
                    session.getPendingRequirements(),
                    null,
                    currentMatch,
                    null,
                    List.of()
            );
        }
    }

    public ProductMatch presentedMatch(long userId, long productId) {
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            session.requireState(ChatState.PRESENTING);
            LastMatch match = Objects.requireNonNull(session.getLastMatch(), "presented match must exist");
            if (match.productId() != productId) {
                throw new IllegalArgumentException("Product is not the current recommendation");
            }
            return Objects.requireNonNull(loadLastMatch(session), "presented match must exist");
        }
    }

    public ChatFlowResponse confirmRequirements(long userId) {
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            session.requireState(ChatState.CONFIRMING);
            Requirements requirements = Objects.requireNonNull(
                    session.getPendingRequirements(),
                    "pending requirements must exist while confirming"
            );
            return search(session, userId, requirements, ChatState.CONFIRMING);
        }
    }

    public ChatFlowResponse retryMatch(long userId) {
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            session.requireState(ChatState.PRESENTING);
            Requirements requirements = Objects.requireNonNull(
                    session.getPendingRequirements(),
                    "pending requirements must exist while presenting"
            );
            return search(session, userId, requirements, ChatState.PRESENTING);
        }
    }

    public ChatFlowResponse cancelConfirmation(long userId) {
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            session.requireState(ChatState.CONFIRMING);
            session.addMessage(MessageRole.ASSISTANT, "Okay, I cancelled that search. Tell me what you are looking for.");
            session.transitionTo(ChatState.GATHERING);
            sessionStore.save(session);
            return response(session, null, null, null, null, List.of());
        }
    }

    public BuyWaitRecommendation recommend(long userId, String urgency) {
        if (urgency == null || urgency.isBlank() || urgency.length() > 200) {
            throw new IllegalArgumentException("Urgency must contain 1 to 200 characters");
        }
        ChatSession session = sessionStore.getOrCreate(userId);
        synchronized (session) {
            session.requireState(ChatState.PRESENTING);
            LastMatch lastMatch = Objects.requireNonNull(session.getLastMatch(), "presented match must exist");
            var listing = listingRepository.findWithProductAndRetailerById(lastMatch.listingId())
                    .orElseThrow(() -> new IllegalStateException("The selected retailer listing is no longer available"));
            if (!Objects.equals(listing.getProduct().getId(), lastMatch.productId())) {
                throw new IllegalStateException("The selected listing no longer matches the presented product");
            }
            PriceHistoryStats stats = priceHistoryService
                    .computeStats(listing.getId(), listing.getPrice())
                    .orElse(null);
            BigDecimal budget = profileService.get(userId).budget();
            BuyWaitRecommendation recommendation = buyWaitAdvisor.advise(
                    listing.getProduct().getCategory(),
                    stats,
                    budget,
                    urgency
            );
            session.addMessage(MessageRole.ASSISTANT,
                    recommendation.decision() + ": " + recommendation.reason());
            sessionStore.save(session);
            return recommendation;
        }
    }

    private ProductMatch loadLastMatch(ChatSession session) {
        LastMatch lastMatch = session.getLastMatch();
        if (lastMatch == null) {
            return null;
        }
        var product = productRepository.findProductWithListingsById(lastMatch.productId())
                .orElseThrow(() -> new IllegalStateException("The presented product is no longer available"));
        var listing = listingRepository.findWithProductAndRetailerById(lastMatch.listingId())
                .orElseThrow(() -> new IllegalStateException("The presented retailer listing is no longer available"));
        if (!Objects.equals(listing.getProduct().getId(), product.getId())) {
            throw new IllegalStateException("The presented listing no longer matches the presented product");
        }
        return new ProductMatch(product, listing, lastMatch.explanation(), lastMatch.offer());
    }

    private ChatFlowResponse search(
            ChatSession session,
            long userId,
            Requirements requirements,
            ChatState failureState
    ) {
        session.transitionTo(ChatState.SEARCHING);
        sessionStore.save(session);
        ProductMatchResult result;
        try {
            result = productMatcher.match(requirements, profileService.get(userId));
        } catch (LLMException | ProductSpecValidationException exception) {
            session.transitionTo(failureState);
            sessionStore.save(session);
            throw exception;
        }
        if (!result.isMatched()) {
            session.addMessage(MessageRole.ASSISTANT, result.noMatchMessage());
            session.transitionTo(ChatState.GATHERING);
            sessionStore.save(session);
            return response(
                    session,
                    null,
                    null,
                    null,
                    result.noMatchMessage(),
                    result.constraintsToRelax()
            );
        }
        ProductMatch match = result.match();
        session.setLastMatch(new LastMatch(
                match.product().getId(),
                match.bestListing().getId(),
                match.explanation(),
                match.offer()
        ));
        session.addMessage(MessageRole.ASSISTANT, match.explanation());
        session.transitionTo(ChatState.PRESENTING);
        sessionStore.save(session);
        return response(session, requirements, null, match, null, List.of());
    }

    private ChatFlowResponse response(
            ChatSession session,
            Requirements requirements,
            String followUpQuestion,
            ProductMatch match,
            String noMatchMessage,
            List<String> constraintsToRelax
    ) {
        return new ChatFlowResponse(
                session.getState(),
                session.getHistory(),
                requirements,
                followUpQuestion,
                match,
                noMatchMessage,
                constraintsToRelax
        );
    }

    private String confirmationText() {
        return "Please confirm the requirements shown.";
    }
}
