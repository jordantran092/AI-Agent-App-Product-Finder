package com.eecs3311.productfinder.application.review;

public record ReviewTrustResult(String trustPhrase) implements ReviewAnalysisResult {

    public ReviewTrustResult {
        if (trustPhrase == null || trustPhrase.isBlank() || trustPhrase.length() > 200) {
            throw new IllegalArgumentException("trustPhrase must contain 1 to 200 characters");
        }
    }
}
