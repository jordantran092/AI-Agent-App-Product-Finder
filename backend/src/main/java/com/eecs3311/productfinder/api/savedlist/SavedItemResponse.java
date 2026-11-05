package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.domain.savedlist.SavedItem;

public record SavedItemResponse(ProductSummaryResponse product, String note, String trustLabel) {

    public static SavedItemResponse from(SavedItem item) {
        return new SavedItemResponse(
                ProductSummaryResponse.from(item.getProduct()),
                item.getNote(),
                item.getTrustLabel()
        );
    }
}
