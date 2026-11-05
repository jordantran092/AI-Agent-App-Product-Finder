package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.ProductPageResult;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;

public record ProductPageResponse(boolean discontinued, ProductSummaryResponse product, String note,
                                  String trustLabel, OfferResponse bestOffer) {

    public static ProductPageResponse discontinuedProduct() {
        return new ProductPageResponse(true, null, null, null, null);
    }

    public static ProductPageResponse from(ProductPageResult result) {
        if (result.discontinued()) {
            return discontinuedProduct();
        }
        return new ProductPageResponse(
                false,
                ProductSummaryResponse.from(result.product()),
                result.savedItem().getNote(),
                result.savedItem().getTrustLabel(),
                result.bestListing() == null ? null : OfferResponse.from(result.bestListing())
        );
    }

    public record OfferResponse(String retailer, BigDecimal price, ProductCondition condition) {
        public static OfferResponse from(Listing listing) {
            return new OfferResponse(listing.getRetailer().getName(), listing.getPrice(), listing.getCondition());
        }
    }
}
