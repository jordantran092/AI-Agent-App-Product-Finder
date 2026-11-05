package com.eecs3311.productfinder.domain.catalog;

public enum ProductCategory {
    SMARTPHONE("smartphone"),
    LAPTOP("laptop"),
    TABLET("tablet"),
    TELEVISION("television"),
    HEADPHONES("headphones"),
    CLOTHING_TOP("clothing-top"),
    CLOTHING_BOTTOM("clothing-bottom"),
    FOOTWEAR("footwear");

    private final String schemaName;

    ProductCategory(String schemaName) {
        this.schemaName = schemaName;
    }

    public String schemaName() {
        return schemaName;
    }

    public static ProductCategory fromSchemaName(String schemaName) {
        for (ProductCategory category : values()) {
            if (category.schemaName.equals(schemaName)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown product category schema: " + schemaName);
    }
}
