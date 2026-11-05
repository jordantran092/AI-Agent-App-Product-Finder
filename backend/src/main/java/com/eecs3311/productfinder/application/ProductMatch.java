package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;

import java.util.Objects;

public record ProductMatch(Product product, Listing bestListing, String explanation) {

    public ProductMatch {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(bestListing, "bestListing must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }
}
