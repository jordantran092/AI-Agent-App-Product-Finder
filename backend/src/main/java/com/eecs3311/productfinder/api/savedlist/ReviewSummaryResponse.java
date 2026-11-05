package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.review.ReviewSummaryResult;

public record ReviewSummaryResponse(String summary) {

    public static ReviewSummaryResponse from(ReviewSummaryResult result) {
        return new ReviewSummaryResponse(result.summary());
    }
}
