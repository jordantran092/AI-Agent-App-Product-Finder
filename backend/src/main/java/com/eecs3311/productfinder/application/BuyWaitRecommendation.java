package com.eecs3311.productfinder.application;

import java.util.Objects;
import java.util.Optional;

public record BuyWaitRecommendation(
        BuyWaitDecision decision,
        String reason,
        PriceHistoryStats priceHistory
) {

    public BuyWaitRecommendation {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public Optional<PriceHistoryStats> optionalPriceHistory() {
        return Optional.ofNullable(priceHistory);
    }
}
