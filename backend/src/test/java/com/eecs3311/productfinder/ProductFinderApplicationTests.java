package com.eecs3311.productfinder;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.infrastructure.persistence.ListingRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product_finder;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class ProductFinderApplicationTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void loadsSyntheticCatalogAcrossAllProductCategories() {
        assertThat(productRepository.count()).isEqualTo(8);
        assertThat(productRepository.findAll())
                .extracting(product -> product.getCategory())
                .containsExactlyInAnyOrder(ProductCategory.values());
    }

    @Test
    void loadsStructuredAttributesListingsAndReviews() {
        var product = productRepository.findByModelIgnoreCase("OR-15-128").orElseThrow();

        assertThat(product.getAttributes()).containsEntry("storageGb", 128);
        assertThat(listingRepository.findByProductId(product.getId())).hasSize(2);
        assertThat(reviewRepository.findByProductIdOrderByReviewedAtDesc(product.getId())).hasSize(2);
    }
}
