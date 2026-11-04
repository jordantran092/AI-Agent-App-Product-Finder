package com.eecs3311.productfinder.application.llm;

import java.util.List;
import java.util.Objects;

public record LLMRequest(
        List<LLMMessage> messages,
        LLMOptions options,
        LLMOutputSchema outputSchema
) {

    public LLMRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        Objects.requireNonNull(options, "options must not be null");
    }

    public LLMRequest(List<LLMMessage> messages, LLMOptions options) {
        this(messages, options, null);
    }
}
