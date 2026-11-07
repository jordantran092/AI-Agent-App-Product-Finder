package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;

public record BestPriceResult(boolean discontinued, Listing bestListing) {
}
