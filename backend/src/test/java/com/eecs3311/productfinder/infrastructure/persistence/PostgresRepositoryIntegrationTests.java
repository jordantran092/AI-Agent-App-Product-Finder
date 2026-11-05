package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.domain.user.UserProfile;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.application.review.ReviewAnalyzer;
import com.eecs3311.productfinder.application.review.ReviewSummaryResult;
import com.eecs3311.productfinder.application.review.ReviewTrustResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private ReviewAnalyzer reviewAnalyzer;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private SavedListService savedListService;

    @BeforeEach
    void stubReviewAnalysis() {
        when(reviewAnalyzer.analyzeTrust(anyLong())).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        when(reviewAnalyzer.summarize(anyLong())).thenReturn(new ReviewSummaryResult("A capable everyday phone."));
    }

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
                Set.of("Pine"), Set.of(ProductCondition.NEW));
        assertThat(settings.excludedBrands()).containsExactly("pine");
        assertThat(productService.bestPrice(user.getId(), productId).orElseThrow().getPrice())
                .isEqualByComparingTo("679.99");

        savedListService.removeProduct(user.getId(), productId);
        assertThat(savedListService.get(user.getId()).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void savedProductApiSupportsDuplicateConfirmationNotesSettingsAndOwnerIsolation() throws Exception {
        MockHttpSession ownerSession = createLoggedInUser("saved-api-owner-" + System.nanoTime());
        long productId = productRepository.findByModelIgnoreCase("OR-15-128").orElseThrow().getId();

        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelName\":\"OR-15-128\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ADDED"))
                .andExpect(jsonPath("$.trustLabel").value("Trustworthy Reviews"));
        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelName\":\"or-15-128\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_SAVED"));

        mockMvc.perform(put("/api/saved-list/items/{productId}/note", productId).session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"keep\\u0001 this\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanitized").value(true))
                .andExpect(jsonPath("$.note").value("keep this"));

        mockMvc.perform(put("/api/profile").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":1200.00,\"excludedBrands\":[\"Pine\"],\"conditions\":[\"NEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedBrands[0]").value("pine"));
        mockMvc.perform(put("/api/profile").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":1200.00,\"excludedBrands\":[\"Not A Brand\"],"
                                + "\"conditions\":[\"NEW\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("unknown excluded brand")));

        mockMvc.perform(get("/api/saved-list").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/products/{productId}", productId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.model").value("OR-15-128"))
                .andExpect(jsonPath("$.note").value("keep this"))
                .andExpect(jsonPath("$.trustLabel").value("Trustworthy Reviews"))
                .andExpect(jsonPath("$.bestOffer.price").value(679.99));
        mockMvc.perform(post("/api/products/{productId}/summary", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("A capable everyday phone."));

        MockHttpSession otherSession = createLoggedInUser("saved-api-other-" + System.nanoTime());
        mockMvc.perform(get("/api/products/{productId}", productId).session(otherSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/products/{productId}/summary", productId)
                        .session(otherSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/saved-list/items/{productId}", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/saved-list").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private MockHttpSession createLoggedInUser(String username) throws Exception {
        String password = "integration-password";
        userRepository.saveAndFlush(new User(username, passwordEncoder.encode(password)));
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
