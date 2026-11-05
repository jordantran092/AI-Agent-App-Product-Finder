package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import com.eecs3311.productfinder.domain.chat.Requirements;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMatcherTests {

    private final ProductSpecSchemaRegistry schemaRegistry =
            new ProductSpecSchemaRegistry(new ObjectMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void filtersHardRequirementsBeforeAskingTheModelToRankCandidates() {
        Product eligible = product(11L, "Orion", Map.of(
                "displaySizeInches", 6.7,
                "resolution", "2400x1080",
                "operatingSystem", "ExampleOS",
                "memoryGb", 8,
                "storageGb", 256,
                "batteryMah", 5000,
                "connectivity", List.of("5G", "Wi-Fi")
        ), new BigDecimal("699.99"), ProductCondition.NEW);
        Product insufficientStorage = product(12L, "Orion", Map.of(
                "displaySizeInches", 6.7,
                "resolution", "2400x1080",
                "operatingSystem", "ExampleOS",
                "memoryGb", 8,
                "storageGb", 64,
                "batteryMah", 5000,
                "connectivity", List.of("5G", "Wi-Fi")
        ), new BigDecimal("399.99"), ProductCondition.NEW);
        Product overBudget = product(13L, "Other", Map.of(
                "displaySizeInches", 6.7,
                "resolution", "2400x1080",
                "operatingSystem", "ExampleOS",
                "memoryGb", 8,
                "storageGb", 512,
                "batteryMah", 5000,
                "connectivity", List.of("5G", "Wi-Fi")
        ), new BigDecimal("899.99"), ProductCondition.NEW);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findWithListingsByCategory(ProductCategory.SMARTPHONE))
                .thenReturn(List.of(eligible, insufficientStorage, overBudget));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class)))
                .thenReturn(new LLMResponse(
                        "{\"productId\":11,\"explanation\":\"It meets the storage requirement and budget.\"}",
                        "test-model",
                        "stop",
                        null
                ));
        ProductMatcher matcher = matcher(products, llm);

        ProductMatchResult result = matcher.match(requirements(), new ProfileSettings(
                new BigDecimal("800.00"),
                Set.of("other"),
                Set.of(ProductCondition.NEW)
        ));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.match().product()).isSameAs(eligible);
        assertThat(result.match().bestListing().getPrice()).isEqualByComparingTo("699.99");
        org.mockito.ArgumentCaptor<LLMRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(LLMRequest.class);
        verify(llm).complete(requestCaptor.capture());
        JsonNode userInput = objectMapper.readTree(requestCaptor.getValue().messages().get(1).content());
        assertThat(userInput.get("eligibleCandidates")).hasSize(1);
        assertThat(userInput.get("eligibleCandidates").get(0).get("productId").longValue()).isEqualTo(11);
    }

    @Test
    void returnsRelaxableConstraintsWithoutCallingTheModelWhenNoCandidateQualifies() {
        Product insufficientStorage = product(21L, "Orion", Map.of(
                "displaySizeInches", 6.7,
                "resolution", "2400x1080",
                "operatingSystem", "ExampleOS",
                "memoryGb", 8,
                "storageGb", 64,
                "batteryMah", 5000,
                "connectivity", List.of("5G", "Wi-Fi")
        ), new BigDecimal("399.99"), ProductCondition.NEW);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findWithListingsByCategory(ProductCategory.SMARTPHONE))
                .thenReturn(List.of(insufficientStorage));
        LLMClient llm = mock(LLMClient.class);
        ProductMatcher matcher = matcher(products, llm);

        ProductMatchResult result = matcher.match(requirements(), ProfileSettings.unrestricted());

        assertThat(result.isMatched()).isFalse();
        assertThat(result.noMatchMessage()).contains("No products meet");
        assertThat(result.constraintsToRelax()).contains("product specifications");
        verify(llm, never()).complete(any(LLMRequest.class));
    }

    @Test
    void refusesAModelSelectionOutsideTheFilteredCandidateSet() {
        Product eligible = product(31L, "Orion", Map.of(
                "displaySizeInches", 6.7,
                "resolution", "2400x1080",
                "operatingSystem", "ExampleOS",
                "memoryGb", 8,
                "storageGb", 256,
                "batteryMah", 5000,
                "connectivity", List.of("5G", "Wi-Fi")
        ), new BigDecimal("699.99"), ProductCondition.NEW);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findWithListingsByCategory(ProductCategory.SMARTPHONE)).thenReturn(List.of(eligible));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class)))
                .thenReturn(new LLMResponse("{\"productId\":999,\"explanation\":\"Invented candidate\"}",
                        "test-model", "stop", null));

        assertThatThrownBy(() -> matcher(products, llm).match(requirements(), ProfileSettings.unrestricted()))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class)
                .hasMessageContaining("outside the eligible candidate set");
    }

    private ProductMatcher matcher(ProductRepository products, LLMClient llm) {
        return new ProductMatcher(
                products,
                new PriceComparisonService(),
                schemaRegistry,
                new SchemaGuidedPromptBuilder(schemaRegistry),
                llm,
                objectMapper
        );
    }

    private Product product(long id, String brand, Map<String, Object> attributes,
                            BigDecimal price, ProductCondition condition) {
        Retailer retailer = new Retailer("Test retailer");
        Product product = new Product("Phone " + id, brand, "MODEL-" + id, ProductCategory.SMARTPHONE,
                "Test description", null, attributes);
        setId(product, id);
        Listing listing = new Listing(product, retailer, price, condition, true);
        setId(listing, id + 100);
        return product;
    }

    private void setId(Object entity, long id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set test entity id", exception);
        }
    }

    private Requirements requirements() {
        return new Requirements(
                ProductCategory.SMARTPHONE,
                new BigDecimal("800.00"),
                List.of("at least 128 GB storage"),
                List.of(),
                List.of(),
                ProductCondition.NEW,
                Map.of("storageGb", 128)
        );
    }
}
