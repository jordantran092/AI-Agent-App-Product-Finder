package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.domain.user.UserProfile;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.SavedListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTests {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("product_finder_test")
            .withUsername("product_finder_test")
            .withPassword("product_finder_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private RetailerRepository retailerRepository;

    @Autowired
    private PricePointRepository pricePointRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SavedListRepository savedListRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private SavedListService savedListService;

    @Test
    void persistsAndQueriesUsersAndSeededCatalogAgainstPostgres() {
        String username = "postgres-it-" + System.nanoTime();
        User savedUser = userRepository.saveAndFlush(new User(username, "$2a$12$hashed-test-value"));

        assertThat(userRepository.findByUsernameIgnoreCase(username))
                .get()
                .extracting(User::getId)
                .isEqualTo(savedUser.getId());

        for (ProductCategory category : ProductCategory.values()) {
            assertThat(productService.findByCategory(category)).hasSize(1);
        }
        var smartphone = productRepository.findByModelIgnoreCase("OR-15-128").orElseThrow();
        assertThat(smartphone.getAttributes()).containsEntry("storageGb", 128);
        var listings = listingRepository.findByProductId(smartphone.getId());
        assertThat(listings).hasSize(2);
        assertThat(pricePointRepository.findByListingIdOrderByRecordedAtAsc(listings.getFirst().getId()))
                .hasSize(2);
        assertThat(reviewRepository.findByProductIdOrderByReviewedAtDesc(smartphone.getId())).hasSize(2);
        assertThat(retailerRepository.findByNameIgnoreCase("Northstar Electronics")).isPresent();

        UserProfile profile = userProfileRepository.saveAndFlush(new UserProfile(savedUser,
                new BigDecimal("900.00"), Set.of("Avoid Brand"), Set.of(ProductCondition.NEW)));
        assertThat(userProfileRepository.findByUserId(savedUser.getId()).orElseThrow().getId())
                .isEqualTo(profile.getId());

        SavedList savedList = new SavedList(savedUser);
        savedList.addItem(smartphone);
        savedList.setShareToken("postgres-it-share-" + System.nanoTime());
        savedListRepository.saveAndFlush(savedList);
        assertThat(savedListRepository.findByUserId(savedUser.getId()).orElseThrow().getId())
                .isEqualTo(savedList.getId());
        assertThat(savedListRepository.findByShareToken(savedList.getShareToken()).orElseThrow().getId())
                .isEqualTo(savedList.getId());
    }

    @Test
    void registersAuthenticatesAndLogsOutUsingPostgresBackedUser() throws Exception {
        String username = "auth-it-" + System.nanoTime();
        String password = "PostgresAuthIntegrationPassword8";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));

        User user = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(passwordEncoder.matches(password, user.getPasswordHash())).isTrue();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void profileAndSavedListServicesApplySettingsAndPriceEligibility() {
        User user = userRepository.saveAndFlush(new User("services-it-" + System.nanoTime(),
                "$2a$12$hashed-test-value"));
        assertThat(savedListService.get(user.getId())).isEmpty();

        SavedList savedList = savedListService.addProduct(user.getId(), "or-15-128");
        long productId = savedList.getItems().getFirst().getProduct().getId();
        assertThat(savedListService.get(user.getId())).isPresent();
        assertThatIllegalStateException()
                .isThrownBy(() -> savedListService.addProduct(user.getId(), "OR-15-128"))
                .withMessageContaining("already");

        profileService.update(user.getId(), new BigDecimal("800.00"), Set.of(" ORION "),
                Set.of(ProductCondition.NEW));
        assertThat(productService.bestPrice(user.getId(), productId)).isEmpty();

        var settings = profileService.update(user.getId(), new BigDecimal("800.00"),
                Set.of("Another Brand"), Set.of(ProductCondition.NEW));
        assertThat(settings.excludedBrands()).containsExactly("another brand");
        assertThat(productService.bestPrice(user.getId(), productId).orElseThrow().getPrice())
                .isEqualByComparingTo("679.99");

        savedListService.removeProduct(user.getId(), productId);
        assertThat(savedListService.get(user.getId()).orElseThrow().isEmpty()).isTrue();
    }
}
