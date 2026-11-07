package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.domain.user.UserProfile;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.application.SessionStore;
import com.eecs3311.productfinder.domain.catalog.OfferSnapshot;
import com.eecs3311.productfinder.domain.chat.ChatState;
import com.eecs3311.productfinder.domain.chat.LastMatch;
import com.eecs3311.productfinder.domain.chat.Requirements;
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
import java.util.List;
import java.util.Map;

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

    @Autowired
    private SessionStore sessionStore;

    @BeforeEach
    void stubReviewAnalysis() {
        when(reviewAnalyzer.analyzeTrust(anyLong())).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        when(reviewAnalyzer.summarize(anyLong())).thenReturn(new ReviewSummaryResult("A capable everyday phone."));
    }

    @Test
    void recommendationSavePersistsPresentedOfferWithoutRecomputingIt() throws Exception {
        String username = "recommendation-owner-" + System.nanoTime();
        MockHttpSession ownerSession = createLoggedInUser(username);
        long userId = userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
        var product = productRepository.findByModelIgnoreCase("SM-S931W-128GB").orElseThrow();
        var listing = listingRepository.findAll().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst().orElseThrow();
        var chat = sessionStore.getOrCreate(userId);
        chat.setPendingRequirements(new Requirements(ProductCategory.SMARTPHONE, null,
                List.of(), List.of(), List.of(), null, Map.of()));
        chat.transitionTo(ChatState.CONFIRMING);
        chat.transitionTo(ChatState.SEARCHING);
        chat.setLastMatch(new LastMatch(product.getId(), listing.getId(), "A good match.",
                new OfferSnapshot("Original retailer", new BigDecimal("777.00"), ProductCondition.USED)));
        chat.transitionTo(ChatState.PRESENTING);
        sessionStore.save(chat);

        mockMvc.perform(get("/api/chat").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.bestOffer.price").value(777.00))
                .andExpect(jsonPath("$.match.bestOffer.retailer").value("Original retailer"));
        mockMvc.perform(post("/api/saved-list/recommendation").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(product.getId() + 1)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/saved-list/recommendation").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.id").value(product.getId()));
        mockMvc.perform(post("/api/saved-list/recommendation").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_SAVED"));
        mockMvc.perform(get("/api/products/{productId}", product.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedOffer.retailer").value("Original retailer"))
                .andExpect(jsonPath("$.savedOffer.price").value(777.00))
                .andExpect(jsonPath("$.savedOffer.condition").value("USED"));

        MockHttpSession otherSession = createLoggedInUser("other-recommendation-" + System.nanoTime());
        mockMvc.perform(post("/api/saved-list/recommendation").session(otherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(product.getId())))
                .andExpect(status().is4xxClientError());
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
            assertThat(productService.findByCategory(category)).hasSize(category == ProductCategory.SMARTPHONE
                    || category == ProductCategory.LAPTOP
                    || category == ProductCategory.TABLET
                    || category == ProductCategory.TELEVISION ? 313 : 312);
        }
        var smartphone = productRepository.findByModelIgnoreCase("SM-S931W-128GB").orElseThrow();
        assertThat(smartphone.getAttributes()).containsEntry("storageGb", 128);
        var listings = listingRepository.findByProductId(smartphone.getId());
        assertThat(listings).hasSize(2);
        assertThat(pricePointRepository.findByListingIdOrderByRecordedAtAsc(listings.getFirst().getId()))
                .hasSize(2);
        assertThat(reviewRepository.findByProductIdOrderByReviewedAtDesc(smartphone.getId())).hasSize(2);
        assertThat(retailerRepository.findByNameIgnoreCase("Best Buy Canada")).isPresent();
        assertThat(retailerRepository.findByNameIgnoreCase("Apple Store")).isPresent();
        assertThat(productService.findByCategory(ProductCategory.LAPTOP).getFirst().getBrand()).isEqualTo("Apple");

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

        SavedList savedList = savedListService.addProduct(user.getId(), "sm-s931w-128gb");
        long productId = savedList.getItems().getFirst().getProduct().getId();
        assertThat(savedListService.get(user.getId())).isPresent();
        assertThatIllegalStateException()
                .isThrownBy(() -> savedListService.addProduct(user.getId(), "SM-S931W-128GB"))
                .withMessageContaining("already");

        profileService.update(user.getId(), new BigDecimal("1500.00"), Set.of(" SAMSUNG "),
                Set.of(ProductCondition.NEW));
        assertThat(productService.bestPrice(user.getId(), productId)).isEmpty();

        var settings = profileService.update(user.getId(), new BigDecimal("1500.00"),
                Set.of("Apple"), Set.of(ProductCondition.NEW));
        assertThat(settings.excludedBrands()).containsExactly("apple");
        assertThat(productService.bestPrice(user.getId(), productId).orElseThrow().getPrice())
                .isEqualByComparingTo("1149.99");

        savedListService.removeProduct(user.getId(), productId);
        assertThat(savedListService.get(user.getId()).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void savedProductApiSupportsDuplicateConfirmationNotesSettingsAndOwnerIsolation() throws Exception {
        MockHttpSession ownerSession = createLoggedInUser("saved-api-owner-" + System.nanoTime());
        long productId = productRepository.findByModelIgnoreCase("SM-S931W-128GB").orElseThrow().getId();
        String productName = productRepository.findById(productId).orElseThrow().getName();

        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"%s\"}".formatted(productName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ADDED"))
                .andExpect(jsonPath("$.trustLabel").value("Trustworthy Reviews"));
        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"%s\"}".formatted(productName.toLowerCase())))
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
                        .content("{\"budget\":1200.00,\"excludedBrands\":[\"Apple\"],\"conditions\":[\"NEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedBrands[0]").value("apple"));
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
                .andExpect(jsonPath("$.product.model").value("SM-S931W-128GB"))
                .andExpect(jsonPath("$.note").value("keep this"))
                .andExpect(jsonPath("$.trustLabel").value("Trustworthy Reviews"))
                .andExpect(jsonPath("$.bestOffer").doesNotExist());
        mockMvc.perform(post("/api/products/{productId}/best-price", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestOffer.price").value(1149.99));
        mockMvc.perform(put("/api/profile").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":100.00,\"excludedBrands\":[],\"conditions\":[\"NEW\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products/{productId}/best-price", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestOffer").isEmpty());
        mockMvc.perform(post("/api/products/{productId}/summary", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("A capable everyday phone."));

        MockHttpSession otherSession = createLoggedInUser("saved-api-other-" + System.nanoTime());
        mockMvc.perform(get("/api/products/{productId}", productId).session(otherSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/products/{productId}/best-price", productId)
                        .session(otherSession).with(csrf()))
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

        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"An Unknown Quantum Spaceship 999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "This product does not exist in our catalog. Try a different product name."));
    }

    @Test
    void sharingCreatesPublicReadOnlyLinkAndRevokesItWhenListBecomesEmpty() throws Exception {
        MockHttpSession ownerSession = createLoggedInUser("share-api-owner-" + System.nanoTime());
        String productName = productRepository.findByModelIgnoreCase("SM-S931W-128GB").orElseThrow().getName();

        mockMvc.perform(post("/api/saved-list/share").session(ownerSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Add a product before sharing"));

        mockMvc.perform(post("/api/saved-list/items").session(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"%s\"}".formatted(productName)))
                .andExpect(status().isCreated());

        MvcResult shareResult = mockMvc.perform(post("/api/saved-list/share")
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareUrl").value(
                        org.hamcrest.Matchers.startsWith("http://localhost:5173/shared/")))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                shareResult.getResponse().getContentAsString(), "$.shareToken");
        assertThat(token).matches("[A-Za-z0-9_-]{43}");

        mockMvc.perform(post("/api/saved-list/share").session(ownerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").value(token));

        mockMvc.perform(get("/api/shared-lists/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].model").value("SM-S931W-128GB"))
                .andExpect(jsonPath("$.items[0].retailer").value("Amazon.ca"))
                .andExpect(jsonPath("$.items[0].price").value(1149.99))
                .andExpect(jsonPath("$.items[0].note").doesNotExist());

        long productId = productRepository.findByModelIgnoreCase("SM-S931W-128GB").orElseThrow().getId();
        mockMvc.perform(delete("/api/saved-list/items/{productId}", productId)
                        .session(ownerSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/shared-lists/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("This list is no longer available"));
        mockMvc.perform(get("/api/shared-lists/{token}", "invalid-token"))
                .andExpect(status().isNotFound());
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
