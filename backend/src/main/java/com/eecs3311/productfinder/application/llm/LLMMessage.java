package com.eecs3311.productfinder.application.llm;

import java.util.Objects;

public record LLMMessage(LLMRole role, String content) {

    public LLMMessage {
        Objects.requireNonNull(role, "role must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
