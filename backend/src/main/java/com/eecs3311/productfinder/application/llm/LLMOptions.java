package com.eecs3311.productfinder.application.llm;

public record LLMOptions(
        Double temperature,
        Integer maxTokens,
        LLMResponseFormat responseFormat
) {

    public LLMOptions {
        if (temperature != null && (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (maxTokens != null && maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (responseFormat == null) {
            responseFormat = LLMResponseFormat.TEXT;
        }
    }

    public static LLMOptions defaults() {
        return new LLMOptions(null, null, LLMResponseFormat.TEXT);
    }
}
