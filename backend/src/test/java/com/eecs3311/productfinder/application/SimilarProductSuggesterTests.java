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

        Optional<Product> suggestion = suggester.suggest("OR-15-12B");

        assertThat(suggestion).containsSame(candidate);
        ArgumentCaptor<LLMRequest> request = ArgumentCaptor.forClass(LLMRequest.class);
        verify(llm).complete(request.capture());
        LLMRequest llmRequest = request.getValue();
        assertThat(llmRequest.outputSchema()).isNotNull();
        assertThat(llmRequest.outputSchema().name()).isEqualTo("similar-product");
        assertThat(llmRequest.messages().get(1).content())
                .contains("OR-15-12B")
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

        assertThatThrownBy(() -> suggester.suggest("OR-15-12B"))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class)
                .hasMessageContaining("outside the candidate set");
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
