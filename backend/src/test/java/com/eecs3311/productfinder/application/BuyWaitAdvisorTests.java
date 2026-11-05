package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyWaitAdvisorTests {

    @Test
    void returnsTypedRecommendationAndSuppliedHistory() {
        ObjectMapper mapper = new ObjectMapper();
        ProductSpecSchemaRegistry schemas = new ProductSpecSchemaRegistry(mapper);
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(new LLMResponse(
                "{\"decision\":\"WAIT\",\"reason\":\"The current price is above the recent average.\"}",
                "test-model",
                "stop",
                null
        ));
        PriceHistoryStats history = new PriceHistoryStats(
                new BigDecimal("500.00"),
                new BigDecimal("450.00"),
                new BigDecimal("550.00"),
                new BigDecimal("50.00")
        );
        BuyWaitAdvisor advisor = new BuyWaitAdvisor(
                llm,
                new SchemaGuidedPromptBuilder(schemas),
                mapper
        );

        BuyWaitRecommendation recommendation = advisor.advise(
                ProductCategory.SMARTPHONE,
                history,
                new BigDecimal("600.00"),
                "I need it within a month"
        );

        assertThat(recommendation.decision()).isEqualTo(BuyWaitDecision.WAIT);
        assertThat(recommendation.reason()).contains("above the recent average");
        assertThat(recommendation.priceHistory()).isEqualTo(history);
    }

    @Test
    void supportsAdviceWithoutPriceHistory() {
        ObjectMapper mapper = new ObjectMapper();
        ProductSpecSchemaRegistry schemas = new ProductSpecSchemaRegistry(mapper);
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(new LLMResponse(
                "{\"decision\":\"BUY_NOW\",\"reason\":\"Your budget and urgency support buying now.\"}",
                "test-model",
                "stop",
                null
        ));
        BuyWaitAdvisor advisor = new BuyWaitAdvisor(llm, new SchemaGuidedPromptBuilder(schemas), mapper);

        BuyWaitRecommendation recommendation = advisor.advise(
                ProductCategory.SMARTPHONE,
                null,
                new BigDecimal("700.00"),
                "urgent"
        );

        assertThat(recommendation.decision()).isEqualTo(BuyWaitDecision.BUY_NOW);
        assertThat(recommendation.priceHistory()).isNull();
        var request = org.mockito.ArgumentCaptor.forClass(LLMRequest.class);
        verify(llm).complete(request.capture());
        assertThat(request.getValue().messages().get(1).content()).contains("\"priceHistory\":null");
    }

    @Test
    void rejectsInvalidModelDecisions() {
        ObjectMapper mapper = new ObjectMapper();
        ProductSpecSchemaRegistry schemas = new ProductSpecSchemaRegistry(mapper);
        LLMClient llm = mock(LLMClient.class);
        when(llm.complete(any(LLMRequest.class))).thenReturn(new LLMResponse(
                "{\"decision\":\"MAYBE\",\"reason\":\"Unclear\"}",
                "test-model",
                "stop",
                null
        ));
        BuyWaitAdvisor advisor = new BuyWaitAdvisor(llm, new SchemaGuidedPromptBuilder(schemas), mapper);

        assertThatThrownBy(() -> advisor.advise(ProductCategory.SMARTPHONE, null, null, "urgent"))
                .isInstanceOf(com.eecs3311.productfinder.application.llm.LLMProtocolException.class);
    }
}
