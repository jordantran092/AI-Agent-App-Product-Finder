package com.eecs3311.productfinder.application.llm;

public record LLMResponse(
        String content,
        String model,
        String finishReason,
        LLMTokenUsage usage
) {

    public LLMResponse {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (finishReason == null || finishReason.isBlank()) {
            throw new IllegalArgumentException("finishReason must not be blank");
        }
    }
}
