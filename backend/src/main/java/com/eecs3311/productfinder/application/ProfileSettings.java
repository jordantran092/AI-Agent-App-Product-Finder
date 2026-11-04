package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;
import java.util.Set;

public record ProfileSettings(
        BigDecimal budget,
        Set<String> excludedBrands,
        Set<ProductCondition> conditions
) {

    public ProfileSettings {
        excludedBrands = Set.copyOf(excludedBrands);
        conditions = Set.copyOf(conditions);
    }

    public static ProfileSettings unrestricted() {
        return new ProfileSettings(null, Set.of(), Set.of());
    }
}
