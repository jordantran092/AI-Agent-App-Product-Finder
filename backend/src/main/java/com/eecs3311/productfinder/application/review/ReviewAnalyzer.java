package com.eecs3311.productfinder.application.review;

public interface ReviewAnalyzer {

    ReviewTrustResult analyzeTrust(long productId);

    ReviewSummaryResult summarize(long productId);
}
