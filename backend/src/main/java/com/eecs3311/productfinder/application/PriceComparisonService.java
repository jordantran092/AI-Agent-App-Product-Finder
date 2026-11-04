package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class PriceComparisonService {

    public Optional<Listing> bestListing(Product product, ProfileSettings profile) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        return product.getListings().stream()
                .filter(Listing::isInStock)
                .filter(listing -> listing.getPrice() != null)
                .filter(listing -> listing.getPrice().signum() >= 0)
                .filter(listing -> profile.conditions().isEmpty()
                        || profile.conditions().contains(listing.getCondition()))
                .filter(listing -> !profile.excludedBrands().contains(
                        listing.getProduct().getBrand().trim().toLowerCase(Locale.ROOT)))
                .min(Comparator.comparing(Listing::getPrice));
    }
}
