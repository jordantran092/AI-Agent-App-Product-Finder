package com.eecs3311.productfinder.application.llm;

public record LLMTokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public LLMTokenUsage {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }
}
