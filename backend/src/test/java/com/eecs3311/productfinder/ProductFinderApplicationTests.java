package com.eecs3311.productfinder;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.ProductSpecSchemaRegistry;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.infrastructure.persistence.ListingRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ReviewRepository;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import com.eecs3311.productfinder.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductSpecSchemaRegistry schemaRegistry;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private SavedListService savedListService;

    @Test
    void contextLoads() {
    }

    @Test
    void loadsSyntheticCatalogAcrossAllProductCategories() {
        assertThat(productRepository.count()).isEqualTo(2500);
        assertThat(productRepository.findAll()).extracting(Product::getModel).doesNotHaveDuplicates();
    }

    @Test
    void loadsStructuredAttributesListingsAndReviews() {
        var product = productService.findByModelName("SM-S931W-128GB");

        assertThat(product.getAttributes()).containsEntry("storageGb", 128);
        assertThat(listingRepository.findByProductId(product.getId())).hasSize(2);
        assertThat(reviewRepository.findByProductIdOrderByReviewedAtDesc(product.getId())).hasSize(2);
        var laptop = productService.findByCategory(ProductCategory.LAPTOP).getFirst();
        assertThat(laptop.getName()).isEqualTo("MacBook Air 13-inch (M4)");
        assertThat(laptop.getBrand()).isEqualTo("Apple");
        assertThat(listingRepository.findByProductId(laptop.getId())).hasSize(3);
    }

    @Test
    void validatesEverySyntheticProductAgainstItsCategorySchema() {
        for (ProductCategory category : ProductCategory.values()) {
            var products = productService.findByCategory(category);
            assertThat(products).hasSize(category == ProductCategory.SMARTPHONE
                    || category == ProductCategory.LAPTOP
                    || category == ProductCategory.TABLET
                    || category == ProductCategory.TELEVISION ? 313 : 312);
            products.forEach(product -> assertThat(schemaRegistry.validate(category, product.getAttributes()))
                    .as("schema validation for %s", product.getModel())
                    .isEmpty());
        }

        var candidates = productRepository.findWithListingsByCategory(ProductCategory.SMARTPHONE);
        assertThat(candidates).hasSize(313);
        assertThat(candidates.getFirst().getListings()).hasSize(2);
        assertThat(candidates.getFirst().getListings().getFirst().getRetailer().getName()).isNotBlank();
        var listing = listingRepository.findWithProductAndRetailerById(
                candidates.getFirst().getListings().getFirst().getId()).orElseThrow();
        assertThat(listing.getProduct().getCategory()).isEqualTo(ProductCategory.SMARTPHONE);
        assertThat(listing.getRetailer().getName()).isNotBlank();

        assertThat(schemaRegistry.validate(ProductCategory.SMARTPHONE,
                java.util.Map.of("storageGb", -1, "unexpected", true))).isNotEmpty();
    }

    @Test
    void exercisesProfileAndSavedListServicesWithTheLocalRepository() {
        String username = "service-smoke-" + System.nanoTime();
        var user = userRepository.saveAndFlush(new User(username, "hashed-test-password"));

        var list = savedListService.addProduct(user.getId(), "SM-S931W-128GB");
        long productId = list.getItems().getFirst().getProduct().getId();
        assertThatIllegalStateException()
                .isThrownBy(() -> savedListService.addProduct(user.getId(), "sm-s931w-128gb"));

        profileService.update(user.getId(), null, java.util.Set.of("samsung"), java.util.Set.of(ProductCondition.NEW));
        assertThat(productService.bestPrice(user.getId(), productId)).isEmpty();

        profileService.update(user.getId(), null, java.util.Set.of(), java.util.Set.of(ProductCondition.NEW));
        assertThat(productService.bestPrice(user.getId(), productId).orElseThrow().getPrice())
                .isEqualByComparingTo("1149.99");

        savedListService.removeProduct(user.getId(), productId);
        assertThat(savedListService.get(user.getId()).orElseThrow().isEmpty()).isTrue();
    }
}
