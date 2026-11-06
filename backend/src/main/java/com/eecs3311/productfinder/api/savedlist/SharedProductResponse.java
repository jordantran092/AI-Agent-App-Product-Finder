package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.SharedProductView;

import java.math.BigDecimal;

public record SharedProductResponse(
        String name,
        String brand,
        String model,
        String imageUrl,
        String retailer,
        BigDecimal price
) {

    public static SharedProductResponse from(SharedProductView view) {
        return new SharedProductResponse(
                view.name(), view.brand(), view.model(), view.imageUrl(), view.retailer(), view.price());
    }
}
