package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.domain.chat.ChatMessage;
import com.eecs3311.productfinder.domain.chat.ChatState;
import com.eecs3311.productfinder.domain.chat.Requirements;

import java.util.List;

public record ChatResponse(
        ChatState state,
        List<ChatMessage> history,
        Requirements requirements,
        String followUpQuestion,
        ChatProductResponse match,
        String noMatchMessage,
        List<String> constraintsToRelax
) {

    public ChatResponse {
        history = List.copyOf(history);
        constraintsToRelax = List.copyOf(constraintsToRelax);
    }
}
