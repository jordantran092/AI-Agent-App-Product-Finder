package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record ProfileUpdateRequest(
        @DecimalMin(value = "0.00", message = "budget must not be negative")
        @Digits(integer = 10, fraction = 2, message = "budget must fit NUMERIC(12,2)")
        BigDecimal budget,
        @NotNull Set<@Size(max = 120) String> excludedBrands,
        @NotNull Set<@NotNull ProductCondition> conditions
) {
}
