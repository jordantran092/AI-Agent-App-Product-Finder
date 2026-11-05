package com.eecs3311.productfinder.domain.chat;

import java.util.Objects;

public record LastMatch(long productId, long listingId, String explanation) {

    public LastMatch {
        if (productId <= 0 || listingId <= 0) {
            throw new IllegalArgumentException("productId and listingId must be positive");
        }
        Objects.requireNonNull(explanation, "explanation must not be null");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }
}
