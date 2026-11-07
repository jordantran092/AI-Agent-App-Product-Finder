package com.eecs3311.productfinder.domain.catalog;

import java.math.BigDecimal;
import java.util.Objects;

public record OfferSnapshot(String retailer, BigDecimal price, ProductCondition condition) {
    public OfferSnapshot {
        Objects.requireNonNull(retailer, "retailer must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        if (retailer.isBlank() || price.signum() < 0) {
            throw new IllegalArgumentException("Offer must have a retailer and a non-negative price");
        }
    }

    public static OfferSnapshot from(Listing listing) {
        return new OfferSnapshot(listing.getRetailer().getName(), listing.getPrice(), listing.getCondition());
    }
}
