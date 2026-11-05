package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.savedlist.SavedItem;

public record ProductPageResult(boolean discontinued, Product product, SavedItem savedItem, Listing bestListing) {
    public static ProductPageResult discontinuedResult() {
        return new ProductPageResult(true, null, null, null);
    }
}
