package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMUnavailableException;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import com.eecs3311.productfinder.domain.chat.ChatSession;
import com.eecs3311.productfinder.domain.chat.ChatState;
import com.eecs3311.productfinder.domain.chat.LastMatch;
import com.eecs3311.productfinder.domain.chat.MessageRole;
import com.eecs3311.productfinder.domain.chat.Requirements;
import com.eecs3311.productfinder.infrastructure.InMemorySessionStore;
import com.eecs3311.productfinder.infrastructure.persistence.ListingRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTests {

    @Test
    void extractsRequirementsAndWaitsForExplicitConfirmation() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        RequirementsExtractor extractor = mock(RequirementsExtractor.class);
        when(extractor.extract(any(), any())).thenReturn(
                RequirementsExtractionResult.ready(requirements())
        );
        AgentService service = service(sessions, extractor, mock(ProductMatcher.class));

        ChatFlowResponse response = service.handleMessage(15, "I need a phone.");

        assertThat(response.state()).isEqualTo(ChatState.CONFIRMING);
        assertThat(response.requirements()).isEqualTo(requirements());
        assertThat(response.history()).extracting("role").contains(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(response.history().getLast().content())
                .isEqualTo("Please confirm the requirements shown.")
                .doesNotContain("smartphone", "800.00", "storageGb", "128");
        verify(extractor).extract(any(), any());
    }

    @Test
    void vagueRequestRemainsInGatheringAndAddsTheFollowUpToHistory() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        RequirementsExtractor extractor = mock(RequirementsExtractor.class);
        when(extractor.extract(any(), any())).thenReturn(
                RequirementsExtractionResult.followUp("Which product category?")
        );
        AgentService service = service(sessions, extractor, mock(ProductMatcher.class));

        ChatFlowResponse response = service.handleMessage(16, "I need something good.");

        assertThat(response.state()).isEqualTo(ChatState.GATHERING);
        assertThat(response.followUpQuestion()).isEqualTo("Which product category?");
        assertThat(response.history().getLast().content()).isEqualTo("Which product category?");
    }

    @Test
    void rejectsSearchUntilTheUserConfirmsRequirements() {
        AgentService service = service(
                new InMemorySessionStore(),
                mock(RequirementsExtractor.class),
                mock(ProductMatcher.class)
        );

        assertThatIllegalStateException().isThrownBy(() -> service.confirmRequirements(17))
                .withMessageContaining("Expected chat state CONFIRMING");
    }

    @Test
    void treatsAMessageDuringConfirmationAsACorrectionAndExtractsAgain() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = sessions.getOrCreate(22);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        RequirementsExtractor extractor = mock(RequirementsExtractor.class);
        when(extractor.extract(any(), any())).thenReturn(
                RequirementsExtractionResult.ready(requirements())
        );
        AgentService service = service(sessions, extractor, mock(ProductMatcher.class));

        ChatFlowResponse response = service.handleMessage(22, "Actually, make that a laptop.");

        assertThat(response.state()).isEqualTo(ChatState.CONFIRMING);
        assertThat(response.history()).extracting("content")
                .contains("Actually, make that a laptop.");
        verify(extractor).extract(any(), any());
    }

    @Test
    void restoresConfirmationStateWhenTheModelIsUnavailableDuringSearch() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = sessions.getOrCreate(18);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        ProductMatcher matcher = mock(ProductMatcher.class);
        when(matcher.match(any(), any())).thenThrow(new LLMUnavailableException("unavailable", null));
        AgentService service = service(sessions, mock(RequirementsExtractor.class), matcher);

        assertThatThrownBy(() -> service.confirmRequirements(18))
                .isInstanceOf(LLMUnavailableException.class);
        assertThat(session.getState()).isEqualTo(ChatState.CONFIRMING);
    }

    @Test
    void restoresPresentationStateWhenRetryingAMatchAndTheModelIsUnavailable() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = presentedSession(sessions, 23, 61, 62);
        ProductMatcher matcher = mock(ProductMatcher.class);
        when(matcher.match(any(), any())).thenThrow(new LLMUnavailableException("unavailable", null));
        AgentService service = service(sessions, mock(RequirementsExtractor.class), matcher);

        assertThatThrownBy(() -> service.retryMatch(23))
                .isInstanceOf(LLMUnavailableException.class);
        assertThat(session.getState()).isEqualTo(ChatState.PRESENTING);
        assertThat(session.getLastMatch()).isNotNull();
    }

    @Test
    void movesToPresentationOnlyAfterTheModelSelectsAnEligibleMatch() throws Exception {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = sessions.getOrCreate(19);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        Product product = new Product("Phone", "Orion", "OR-1", ProductCategory.SMARTPHONE,
                "Test phone", null, Map.of());
        setId(product, 41L);
        Listing listing = new Listing(product, new Retailer("Retailer A"), new BigDecimal("500.00"),
                ProductCondition.NEW, true);
        setId(listing, 42L);
        ProductMatch match = new ProductMatch(product, listing, "It satisfies the storage requirement.");
        ProductMatcher matcher = mock(ProductMatcher.class);
        when(matcher.match(any(), any())).thenReturn(ProductMatchResult.matched(match));
        AgentService service = service(sessions, mock(RequirementsExtractor.class), matcher);

        ChatFlowResponse response = service.confirmRequirements(19);

        assertThat(response.state()).isEqualTo(ChatState.PRESENTING);
        assertThat(response.match()).isEqualTo(match);
        assertThat(session.getLastMatch().productId()).isEqualTo(41L);
        assertThat(session.getLastMatch().listingId()).isEqualTo(42L);
    }

    @Test
    void noMatchReturnsToGatheringAndExplainsConstraintsToRelax() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = sessions.getOrCreate(20);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        ProductMatcher matcher = mock(ProductMatcher.class);
        when(matcher.match(any(), any())).thenReturn(ProductMatchResult.noMatch(
                "No products meet the selected filters.",
                List.of("product specifications", "maximum price")
        ));
        AgentService service = service(sessions, mock(RequirementsExtractor.class), matcher);

        ChatFlowResponse response = service.confirmRequirements(20);

        assertThat(response.state()).isEqualTo(ChatState.GATHERING);
        assertThat(response.noMatchMessage()).contains("No products meet");
        assertThat(response.constraintsToRelax()).containsExactly("product specifications", "maximum price");
        assertThat(session.getPendingRequirements()).isNull();
    }

    @Test
    void recommendationUsesBudgetAndUrgencyWhenThereIsNoPriceHistory() throws Exception {
        InMemorySessionStore sessions = new InMemorySessionStore();
        ChatSession session = sessions.getOrCreate(21);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        session.transitionTo(ChatState.SEARCHING);
        session.setLastMatch(new LastMatch(51, 52, "Good fit.",
                new com.eecs3311.productfinder.domain.catalog.OfferSnapshot(
                        "Retailer A", new BigDecimal("500.00"), ProductCondition.NEW)));
        session.transitionTo(ChatState.PRESENTING);
        Product product = new Product("Phone", "Orion", "OR-51", ProductCategory.SMARTPHONE,
                "Test phone", null, Map.of());
        setId(product, 51);
        Listing listing = new Listing(product, new Retailer("Retailer A"), new BigDecimal("500.00"),
                ProductCondition.NEW, true);
        setId(listing, 52);

        ProfileService profiles = mock(ProfileService.class);
        when(profiles.get(21)).thenReturn(new ProfileSettings(
                new BigDecimal("600.00"),
                Set.of(),
                Set.of(ProductCondition.NEW)
        ));
        PriceHistoryService history = mock(PriceHistoryService.class);
        when(history.computeStats(52, new BigDecimal("500.00"))).thenReturn(Optional.empty());
        BuyWaitAdvisor advisor = mock(BuyWaitAdvisor.class);
        BuyWaitRecommendation expected = new BuyWaitRecommendation(
                BuyWaitDecision.BUY_NOW,
                "Your stated urgency and budget support buying now.",
                null
        );
        when(advisor.advise(ProductCategory.SMARTPHONE, null, new BigDecimal("600.00"), "urgent"))
                .thenReturn(expected);
        ListingRepository listings = mock(ListingRepository.class);
        when(listings.findWithProductAndRetailerById(52L)).thenReturn(Optional.of(listing));
        AgentService service = new AgentService(
                sessions,
                profiles,
                mock(RequirementsExtractor.class),
                mock(ProductMatcher.class),
                history,
                advisor,
                listings,
                mock(ProductRepository.class),
                mock(SimilarProductSuggester.class)
        );

        assertThat(service.recommend(21, "urgent")).isEqualTo(expected);
        verify(advisor).advise(ProductCategory.SMARTPHONE, null, new BigDecimal("600.00"), "urgent");
    }

    private AgentService service(
            InMemorySessionStore sessions,
            RequirementsExtractor extractor,
            ProductMatcher matcher
    ) {
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.get(org.mockito.ArgumentMatchers.anyLong())).thenReturn(ProfileSettings.unrestricted());
        PriceHistoryService priceHistory = mock(PriceHistoryService.class);
        when(priceHistory.computeStats(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(Optional.empty());
        BuyWaitAdvisor advisor = mock(BuyWaitAdvisor.class);
        ListingRepository listings = mock(ListingRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        return new AgentService(
                sessions,
                profiles,
                extractor,
                matcher,
                priceHistory,
                advisor,
                listings,
                products,
                mock(SimilarProductSuggester.class)
        );
    }

    private Requirements requirements() {
        return new Requirements(
                ProductCategory.SMARTPHONE,
                new BigDecimal("800.00"),
                List.of(),
                List.of(),
                List.of(),
                ProductCondition.NEW,
                Map.of("storageGb", 128)
        );
    }

    private ChatSession presentedSession(InMemorySessionStore sessions, long userId, long productId, long listingId) {
        ChatSession session = sessions.getOrCreate(userId);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        session.transitionTo(ChatState.SEARCHING);
        session.setLastMatch(new LastMatch(productId, listingId, "Good fit.",
                new com.eecs3311.productfinder.domain.catalog.OfferSnapshot(
                        "Retailer A", new BigDecimal("500.00"), ProductCondition.NEW)));
        session.transitionTo(ChatState.PRESENTING);
        return session;
    }

    private void setId(Object entity, long id) throws ReflectiveOperationException {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
