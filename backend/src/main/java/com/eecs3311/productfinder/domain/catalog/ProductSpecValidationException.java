package com.eecs3311.productfinder.domain.catalog;

import java.util.List;

public class ProductSpecValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public ProductSpecValidationException(ProductCategory category, List<String> violations) {
        super("Invalid " + category.schemaName() + " product specifications: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
