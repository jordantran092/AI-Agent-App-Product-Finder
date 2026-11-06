package com.eecs3311.productfinder.application.review;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import com.eecs3311.productfinder.domain.catalog.Review;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LLMReviewAnalyzerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void trustAnalysisUsesTypedStructuredOutputAndModelChosenPhrase() {
        LLMClient llmClient = mock(LLMClient.class);
        when(llmClient.complete(any(LLMRequest.class))).thenReturn(response(
                "{\"trustPhrase\":\"Mixed Review Signals\"}"));
        LLMReviewAnalyzer analyzer = new LLMReviewAnalyzer(llmClient, objectMapper);

        Product product = new Product("iPhone 16", "Apple", "A3081", ProductCategory.SMARTPHONE,
                "Test phone.", null, Map.of());
        ReviewTrustResult result = analyzer.analyzeTrust(product, List.of(review("Retailer A", "Works well.")));

        assertThat(result.trustPhrase()).isEqualTo("Mixed Review Signals");
        ArgumentCaptor<LLMRequest> request = ArgumentCaptor.forClass(LLMRequest.class);
        verify(llmClient).complete(request.capture());
        assertThat(request.getValue().outputSchema().name()).isEqualTo("review-trust");
        assertThat(request.getValue().messages().getFirst().content())
                .contains("about")
                .contains("90%")
                .contains("never mention")
                .contains("strong positive prior")
                .contains("not as proof");
        assertThat(request.getValue().messages().get(1).content())
                .contains("\"brand\":\"Apple\"")
                .contains("\"name\":\"iPhone 16\"");
    }

    @Test
    void noReviewsReturnTheDesignatedTrustPhraseWithoutAnLlmCall() {
        LLMClient llmClient = mock(LLMClient.class);
        LLMReviewAnalyzer analyzer = new LLMReviewAnalyzer(llmClient, objectMapper);

        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE,
                "Description", null, Map.of());
        assertThat(analyzer.analyzeTrust(product, List.of()).trustPhrase()).isEqualTo("No reviews to check");

        verifyNoInteractions(llmClient);
    }

    @Test
    void summaryUsesASeparateTypedSchemaAndIncludesProductAndReviewData() {
        Product product = new Product(
                "Orion 15", "Orion", "OR-15-128", ProductCategory.SMARTPHONE, "A useful phone.", null,
                Map.of("storageGb", 128));
        Retailer retailer = new Retailer("Retailer A");
        new com.eecs3311.productfinder.domain.catalog.Listing(
                product, retailer, new BigDecimal("679.99"), ProductCondition.NEW, true);
        LLMClient llmClient = mock(LLMClient.class);
        when(llmClient.complete(any(LLMRequest.class))).thenReturn(response(
                "{\"summary\":\"A 128 GB phone with positive user feedback.\"}"));
        LLMReviewAnalyzer analyzer = new LLMReviewAnalyzer(llmClient, objectMapper);

        ReviewSummaryResult result = analyzer.summarize(product, List.of(review("Retailer A", "Very reliable.")));

        assertThat(result.summary()).contains("positive");
        ArgumentCaptor<LLMRequest> request = ArgumentCaptor.forClass(LLMRequest.class);
        verify(llmClient).complete(request.capture());
        assertThat(request.getValue().outputSchema().name()).isEqualTo("product-review-summary");
        assertThat(request.getValue().messages().get(1).content())
                .contains("Orion 15")
                .contains("679.99")
                .contains("Very reliable.");
    }

    @Test
    void rejectsMalformedOrInvalidStructuredReviewOutput() {
        LLMClient llmClient = mock(LLMClient.class);
        when(llmClient.complete(any(LLMRequest.class))).thenReturn(response("{\"trustPhrase\":\" \"}"));
        LLMReviewAnalyzer analyzer = new LLMReviewAnalyzer(llmClient, objectMapper);
        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE,
                "Description", null, Map.of());

        assertThatThrownBy(() -> analyzer.analyzeTrust(product, List.of(review("Retailer A", "Review."))))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class)
                .hasMessageContaining("trustPhrase");
    }

    @Test
    void rejectsTrustLabelsThatMentionGeneratedCatalogData() {
        LLMClient llmClient = mock(LLMClient.class);
        when(llmClient.complete(any(LLMRequest.class)))
                .thenReturn(response("{\"trustPhrase\":\"Synthetic Reviews\"}"));
        LLMReviewAnalyzer analyzer = new LLMReviewAnalyzer(llmClient, objectMapper);
        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE,
                "Description", null, Map.of());

        assertThatThrownBy(() -> analyzer.analyzeTrust(product, List.of(review("Retailer A", "Review."))))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class)
                .hasMessageContaining("must not mention generated catalog data");
    }

    private static Review review(String retailer, String text) {
        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE, "Description",
                null, Map.of());
        return new Review(product, new Retailer(retailer), new BigDecimal("4.5"), text,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    private static LLMResponse response(String content) {
        return new LLMResponse(content, "test-model", "stop", null);
    }
}
