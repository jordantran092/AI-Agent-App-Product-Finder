package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.application.BuyWaitDecision;
import com.eecs3311.productfinder.application.PriceHistoryStats;

public record BuyWaitResponse(
        BuyWaitDecision decision,
        String reason,
        PriceHistoryStats priceHistory
) {
}
