package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.ProductAddResult;

public record AddProductResponse(String status, ProductSummaryResponse product, String trustLabel, String message) {

    public static AddProductResponse from(ProductAddResult result) {
        String message = switch (result.status()) {
            case ADDED -> "Product added to your saved list";
            case ALREADY_SAVED -> "Product is already in your saved list";
            case SUGGESTION -> "Did you mean this product? Confirm to add it";
            case NOT_FOUND -> "Product not found";
        };
        ProductSummaryResponse product = result.product() == null
                ? null : ProductSummaryResponse.from(result.product());
        return new AddProductResponse(result.status().name(), product, result.trustLabel(), message);
    }
}
