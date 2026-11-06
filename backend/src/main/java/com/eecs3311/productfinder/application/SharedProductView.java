package com.eecs3311.productfinder.application;

import java.math.BigDecimal;

public record SharedProductView(
        String name,
        String brand,
        String model,
        String imageUrl,
        String retailer,
        BigDecimal price
) {
}
