package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarProductSuggesterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void asksTheModelToChooseAmongOnlySimilarCatalogCandidates() {
        Product candidate = product(11L, "Orion 15", "Orion", "OR-15-128");
        Product unrelated = product(12L, "Vista OLED 55", "Vista", "VO-55-4K");
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(candidate, unrelated));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(response("{\"productId\":11}"));
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        Optional<Product> suggestion = suggester.suggest("Orion 15 128GB");

        assertThat(suggestion).containsSame(candidate);
        ArgumentCaptor<LLMRequest> request = ArgumentCaptor.forClass(LLMRequest.class);
        verify(llm).complete(request.capture());
        LLMRequest llmRequest = request.getValue();
        assertThat(llmRequest.outputSchema()).isNotNull();
        assertThat(llmRequest.outputSchema().name()).isEqualTo("similar-product");
        assertThat(llmRequest.options().maxTokens()).isEqualTo(1024);
        assertThat(llmRequest.messages().get(1).content())
                .contains("\"productName\":\"Orion 15 128GB\"")
                .contains("\"productId\":11")
                .doesNotContain("\"productId\":12");
    }

    @Test
    void returnsEmptyWithoutCallingTheModelWhenNoCandidatesAreSimilar() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(product(21L, "Orion 15", "Orion", "OR-15-128")));
        LLMClient llm = mock(LLMClient.class);
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        assertThat(suggester.suggest("unrelated device")).isEmpty();

        verify(llm, never()).complete(any());
    }

    @Test
    void rejectsAModelChoiceOutsideTheCandidateSet() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(product(31L, "Orion 15", "Orion", "OR-15-128")));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(response("{\"productId\":999}"));
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        assertThatThrownBy(() -> suggester.suggest("Orion 15 128GB"))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class)
                .hasMessageContaining("outside the candidate set");
    }

    @Test
    void findsNameCandidatesForAroughAndMisspelledProductName() {
        Product iphone = product(41L, "iPhone 16 128 GB", "Apple", "A3081");
        Product unrelated = product(42L, "Galaxy S25 128 GB", "Samsung", "SM-S931W");
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(iphone, unrelated));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(response("{\"productId\":41}"));
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        assertThat(suggester.suggest("iPhone 15")).containsSame(iphone);
    }

    @Test
    void suggestsAnExactProductNamePhraseWithoutCallingTheModel() {
        Product iphone = product(43L, "iPhone 16 128 GB Black", "Apple", "A3081-128-BLK");
        Product unrelated = product(44L, "Galaxy S25 128 GB", "Samsung", "SM-S931W");
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(iphone, unrelated));
        LLMClient llm = mock(LLMClient.class);
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        assertThat(suggester.suggest("iphone 16")).containsSame(iphone);

        verify(llm, never()).complete(any());
    }

    @Test
    void returnsNoSuggestionWhenTheModelSaysNoCandidateIsHighlySimilar() {
        Product candidate = product(51L, "iPhone 16 128 GB", "Apple", "A3081");
        ProductRepository products = mock(ProductRepository.class);
        when(products.findAll()).thenReturn(List.of(candidate));
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(response("{\"productId\":null}"));
        SimilarProductSuggester suggester = new SimilarProductSuggester(products, llm, objectMapper);

        assertThat(suggester.suggest("iPhone 15")).isEmpty();
        ArgumentCaptor<LLMRequest> request = ArgumentCaptor.forClass(LLMRequest.class);
        verify(llm).complete(request.capture());
        new com.eecs3311.productfinder.application.llm.StructuredOutputValidator(objectMapper)
                .validate("{\"productId\":null}", request.getValue().outputSchema());
    }

    private static Product product(long id, String name, String brand, String model) {
        Product product = new Product(name, brand, model, ProductCategory.SMARTPHONE, "test product", null, Map.of());
        try {
            Field idField = Product.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(product, id);
            return product;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static LLMResponse response(String content) {
        return new LLMResponse(content, "test-model", "stop", null);
    }
}
