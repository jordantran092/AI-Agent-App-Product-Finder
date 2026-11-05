package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class BuyWaitAdvisor {

    private static final String ADVICE_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["decision", "reason"],
              "properties": {
                "decision": {"enum": ["BUY_NOW", "WAIT"]},
                "reason": {"type": "string", "minLength": 1}
              }
            }
            """;

    private final LLMClient llmClient;
    private final SchemaGuidedPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public BuyWaitAdvisor(
            LLMClient llmClient,
            SchemaGuidedPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public BuyWaitRecommendation advise(
            ProductCategory category,
            PriceHistoryStats priceHistory,
            BigDecimal budget,
            String urgency
    ) {
        Objects.requireNonNull(category, "category must not be null");
        if (budget != null && budget.signum() < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        if (urgency == null || urgency.isBlank() || urgency.length() > 200) {
            throw new IllegalArgumentException("urgency must contain 1 to 200 characters");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("priceHistory", priceHistory);
        input.put("budget", budget);
        input.put("urgency", urgency.trim());
        LLMOutputSchema outputSchema = new LLMOutputSchema("buy-wait-recommendation", ADVICE_SCHEMA);
        var request = promptBuilder.build(
                category,
                """
                Advise whether the user should buy the product now or wait.
                Consider the provided price history when available, the user's budget, and their stated urgency.
                When price history is null, use only budget, urgency, and any directly relevant product specifications.
                Do not claim that a price will change or invent historical data.
                Return BUY_NOW or WAIT and a concise reason.
                """,
                java.util.List.of(new LLMMessage(LLMRole.USER, writeJson(input))),
                outputSchema
        );
        JsonNode response;
        try {
            response = objectMapper.readTree(llmClient.complete(request).content());
        } catch (JacksonException exception) {
            throw new LLMProtocolException("Buy/wait advisor returned malformed JSON", exception);
        }
        if (response == null || !response.isObject()) {
            throw new LLMProtocolException("Buy/wait advisor response must be a JSON object");
        }
        JsonNode decisionNode = response.get("decision");
        JsonNode reasonNode = response.get("reason");
        if (decisionNode == null || !decisionNode.isString()
                || reasonNode == null || !reasonNode.isString() || reasonNode.stringValue().isBlank()) {
            throw new LLMProtocolException("Buy/wait advisor returned an invalid recommendation");
        }
        BuyWaitDecision decision;
        try {
            decision = BuyWaitDecision.valueOf(decisionNode.stringValue());
        } catch (IllegalArgumentException exception) {
            throw new LLMProtocolException("Buy/wait advisor returned an unsupported decision", exception);
        }
        return new BuyWaitRecommendation(decision, reasonNode.stringValue(), priceHistory);
    }

    private String writeJson(Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize buy/wait input", exception);
        }
    }
}
