package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.ProfileSettings;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;
import java.util.Set;

public record ProfileSettingsResponse(BigDecimal budget, Set<String> excludedBrands,
                                      Set<ProductCondition> conditions) {

    public ProfileSettingsResponse {
        excludedBrands = Set.copyOf(excludedBrands);
        conditions = Set.copyOf(conditions);
    }

    public static ProfileSettingsResponse from(ProfileSettings settings) {
        return new ProfileSettingsResponse(settings.budget(), settings.excludedBrands(), settings.conditions());
    }
}
