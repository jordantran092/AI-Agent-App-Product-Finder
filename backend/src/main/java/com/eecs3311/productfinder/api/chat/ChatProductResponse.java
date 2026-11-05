package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;

import java.util.Map;

public record ChatProductResponse(
        long productId,
        String name,
        String brand,
        String model,
        String description,
        String imageUrl,
        ProductCategory category,
        Map<String, Object> attributes,
        ChatOfferResponse bestOffer,
        String explanation
) {

    public ChatProductResponse {
        attributes = Map.copyOf(attributes);
    }
}
