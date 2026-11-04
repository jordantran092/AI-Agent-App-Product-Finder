package com.eecs3311.productfinder.domain.chat;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record Requirements(
        ProductCategory category,
        BigDecimal maxPrice,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<String> excludedBrands,
        ProductCondition condition
) {

    public Requirements {
        Objects.requireNonNull(category, "category must not be null");
        mustHaves = List.copyOf(mustHaves);
        niceToHaves = List.copyOf(niceToHaves);
        excludedBrands = List.copyOf(excludedBrands);
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must not be negative");
        }
    }
}
