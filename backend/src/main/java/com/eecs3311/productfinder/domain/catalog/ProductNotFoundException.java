package com.eecs3311.productfinder.domain.catalog;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("Product not found: " + productId);
    }

    public ProductNotFoundException(String model) {
        super("Product not found for model: " + model);
    }
}
