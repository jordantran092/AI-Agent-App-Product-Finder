package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.user.ProfileValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProfileServiceTests {

    private final ProfileService profileService = new ProfileService(null, null);

    @Test
    void normalizesExcludedBrandsAndRetainsAllowedConditions() {
        ProfileSettings settings = profileService.validate(new BigDecimal("1500.00"),
                Set.of("  Example Brand  "), Set.of(ProductCondition.NEW));

        assertThat(settings.budget()).isEqualByComparingTo("1500.00");
        assertThat(settings.excludedBrands()).containsExactly("example brand");
        assertThat(settings.conditions()).containsExactly(ProductCondition.NEW);
    }

    @Test
    void rejectsNegativeOrUnrepresentableBudgetsAndBlankBrands() {
        assertThatExceptionOfType(ProfileValidationException.class)
                .isThrownBy(() -> profileService.validate(new BigDecimal("-0.01"), Set.of(" "), Set.of()))
                .satisfies(exception -> assertThat(exception.getViolations())
                        .contains("budget must not be negative", "excluded brands must not be blank"));

        assertThatExceptionOfType(ProfileValidationException.class)
                .isThrownBy(() -> profileService.validate(new BigDecimal("10000000000.00"),
                        Set.of(), Set.of()));
    }

    @Test
    void rejectsNullAllowedConditionEntries() {
        Set<ProductCondition> conditions = new HashSet<>();
        conditions.add(null);

        assertThatExceptionOfType(ProfileValidationException.class)
                .isThrownBy(() -> profileService.validate(null, Set.of(), conditions))
                .satisfies(exception -> assertThat(exception.getViolations())
                        .contains("allowed conditions must not contain null"));
    }
}
