package com.eecs3311.productfinder.api.savedlist;

import jakarta.validation.constraints.Positive;

public record SaveRecommendationRequest(@Positive long productId) {
}