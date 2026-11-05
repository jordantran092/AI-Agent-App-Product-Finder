package com.eecs3311.productfinder.application;

import java.math.BigDecimal;
import java.util.Objects;

public record PriceHistoryStats(
        BigDecimal averagePrice,
        BigDecimal lowestPrice,
        BigDecimal currentPrice,
        BigDecimal differenceFromAverage
) {

    public PriceHistoryStats {
        Objects.requireNonNull(averagePrice, "averagePrice must not be null");
        Objects.requireNonNull(lowestPrice, "lowestPrice must not be null");
        Objects.requireNonNull(currentPrice, "currentPrice must not be null");
        Objects.requireNonNull(differenceFromAverage, "differenceFromAverage must not be null");
    }
}
