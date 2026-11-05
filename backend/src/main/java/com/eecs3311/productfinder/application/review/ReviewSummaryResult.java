package com.eecs3311.productfinder.application.review;

public record ReviewSummaryResult(String summary) implements ReviewAnalysisResult {

    public ReviewSummaryResult {
        if (summary == null || summary.isBlank() || summary.length() > 8000) {
            throw new IllegalArgumentException("summary must contain 1 to 8000 characters");
        }
    }
}
