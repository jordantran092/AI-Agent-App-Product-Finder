package com.eecs3311.productfinder.domain.chat;

import java.util.Objects;
import com.eecs3311.productfinder.domain.catalog.OfferSnapshot;

public record LastMatch(long productId, long listingId, String explanation, OfferSnapshot offer) {

    public LastMatch {
        if (productId <= 0 || listingId <= 0) {
            throw new IllegalArgumentException("productId and listingId must be positive");
        }
        Objects.requireNonNull(explanation, "explanation must not be null");
        Objects.requireNonNull(offer, "offer must not be null");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }
}
