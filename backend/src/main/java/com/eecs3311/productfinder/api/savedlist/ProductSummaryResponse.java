package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;

import java.util.Map;

public record ProductSummaryResponse(
        long id,
        String name,
        String brand,
        String model,
        ProductCategory category,
        String description,
        String imageUrl,
        Map<String, Object> attributes
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(), product.getName(), product.getBrand(), product.getModel(),
                product.getCategory(), product.getDescription(), product.getImageUrl(), product.getAttributes()
        );
    }
}
