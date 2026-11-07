package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.application.AgentService;
import com.eecs3311.productfinder.application.BuyWaitRecommendation;
import com.eecs3311.productfinder.application.ChatFlowResponse;
import com.eecs3311.productfinder.application.ProductMatch;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgentService agentService;
    private final UserRepository userRepository;

    public ChatController(AgentService agentService, UserRepository userRepository) {
        this.agentService = agentService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ChatResponse current(@AuthenticationPrincipal UserDetails principal) {
        return toResponse(agentService.current(userId(principal)));
    }

    @PostMapping("/messages")
    public ChatResponse postMessage(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return toResponse(agentService.handleMessage(userId(principal), request.text()));
    }

    @PostMapping("/confirm")
    public ChatResponse confirm(@AuthenticationPrincipal UserDetails principal) {
        return toResponse(agentService.confirmRequirements(userId(principal)));
    }

    @PostMapping("/cancel")
    public ChatResponse cancel(@AuthenticationPrincipal UserDetails principal) {
        return toResponse(agentService.cancelConfirmation(userId(principal)));
    }

    @PostMapping("/retry")
    public ChatResponse retry(@AuthenticationPrincipal UserDetails principal) {
        return toResponse(agentService.retryMatch(userId(principal)));
    }

    @PostMapping("/buy-or-wait")
    public BuyWaitResponse buyOrWait(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UrgencyRequest request
    ) {
        BuyWaitRecommendation recommendation = agentService.recommend(userId(principal), request.urgency());
        return new BuyWaitResponse(
                recommendation.decision(),
                recommendation.reason(),
                recommendation.priceHistory()
        );
    }

    private long userId(UserDetails principal) {
        User user = userRepository.findByUsernameIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
        return user.getId();
    }

    private ChatResponse toResponse(ChatFlowResponse flow) {
        return new ChatResponse(
                flow.state(),
                flow.history(),
                flow.requirements(),
                flow.followUpQuestion(),
                flow.match() == null ? null : toProductResponse(flow.match()),
                flow.noMatchMessage(),
                flow.constraintsToRelax()
        );
    }

    private ChatProductResponse toProductResponse(ProductMatch match) {
        var product = match.product();
        var offer = match.offer();
        return new ChatProductResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getModel(),
                product.getDescription(),
                product.getImageUrl(),
                product.getCategory(),
                product.getAttributes(),
                new ChatOfferResponse(offer.retailer(), offer.price(), offer.condition()),
                match.explanation()
        );
    }
}
