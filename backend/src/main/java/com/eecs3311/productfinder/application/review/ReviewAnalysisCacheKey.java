package com.eecs3311.productfinder.application.review;

import java.util.Objects;

public record ReviewAnalysisCacheKey(long productId, ReviewAnalysisType analysisType, String contentFingerprint) {

    public ReviewAnalysisCacheKey {
        if (productId < 1) {
            throw new IllegalArgumentException("productId must be positive");
        }
        Objects.requireNonNull(analysisType, "analysisType must not be null");
        if (contentFingerprint == null || contentFingerprint.isBlank()) {
            throw new IllegalArgumentException("contentFingerprint must not be blank");
        }
    }
}
