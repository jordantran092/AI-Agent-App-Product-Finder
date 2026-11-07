package com.eecs3311.productfinder.api.savedlist;

public record BestPriceResponse(boolean discontinued, ProductPageResponse.OfferResponse bestOffer) {
}
