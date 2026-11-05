package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PriceComparisonServiceTests {

    private final PriceComparisonService service = new PriceComparisonService();

    @Test
    void selectsLowestListingAfterStockPriceAndProfileFiltering() {
        Product product = new Product("Phone", "Example", "EX-1", ProductCategory.SMARTPHONE, "Synthetic", null,
                Map.of());
        Listing usedCheaper = new Listing(product, new Retailer("Used Outlet"), new BigDecimal("100.00"),
                ProductCondition.USED, true);
        new Listing(product, new Retailer("Out of Stock"), new BigDecimal("50.00"), ProductCondition.NEW, false);
        new Listing(product, new Retailer("No Price"), null, ProductCondition.NEW, true);
        new Listing(product, new Retailer("Retailer A"), new BigDecimal("240.00"), ProductCondition.NEW, true);
        Listing lowestNew = new Listing(product, new Retailer("Retailer B"), new BigDecimal("220.00"),
                ProductCondition.NEW, true);

        var best = service.bestListing(product, new ProfileSettings(null, Set.of(), Set.of(ProductCondition.NEW)));

        assertThat(best).contains(lowestNew);
        assertThat(best.orElseThrow().getRetailer().getName()).isEqualTo("Retailer B");
    }

    @Test
    void excludesProductBrandsWithoutCaseSensitivity() {
        Product product = new Product("Phone", "Example", "EX-1", ProductCategory.SMARTPHONE, "Synthetic", null,
                Map.of());
        new Listing(product, new Retailer("Retailer A"), new BigDecimal("240.00"), ProductCondition.NEW, true);

        assertThat(service.bestListing(product,
                new ProfileSettings(null, Set.of("example"), Set.of()))).isEmpty();
    }

    @Test
    void appliesProfileBudgetAndRequestConditionAsHardFilters() {
        Product product = new Product("Phone", "Example", "EX-1", ProductCategory.SMARTPHONE, "Synthetic", null,
                Map.of());
        Listing withinBudget = new Listing(product, new Retailer("Within Budget"), new BigDecimal("200.00"),
                ProductCondition.USED, true);
        new Listing(product, new Retailer("Above Budget"), new BigDecimal("300.00"),
                ProductCondition.NEW, true);

        assertThat(service.bestListing(
                product,
                new ProfileSettings(new BigDecimal("250.00"), Set.of(), Set.of()),
                new BigDecimal("250.00"),
                ProductCondition.NEW
        )).isEmpty();
        assertThat(service.bestListing(
                product,
                new ProfileSettings(new BigDecimal("250.00"), Set.of(), Set.of()),
                new BigDecimal("250.00"),
                ProductCondition.USED
        )).contains(withinBudget);
    }
}
