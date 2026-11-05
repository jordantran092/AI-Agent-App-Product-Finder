package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Product;

public record ProductAddResult(ProductAddStatus status, Product product, String trustLabel) {
}
