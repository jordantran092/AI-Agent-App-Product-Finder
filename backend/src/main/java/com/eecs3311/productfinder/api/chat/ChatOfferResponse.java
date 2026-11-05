package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;

public record ChatOfferResponse(String retailer, BigDecimal price, ProductCondition condition) {
}
