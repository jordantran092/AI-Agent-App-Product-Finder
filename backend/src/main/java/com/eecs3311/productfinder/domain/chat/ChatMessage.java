package com.eecs3311.productfinder.domain.chat;

import java.time.OffsetDateTime;
import java.util.Objects;

public record ChatMessage(MessageRole role, String content, OffsetDateTime sentAt) {

    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(sentAt, "sentAt must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("message content must not be blank");
        }
    }
}
