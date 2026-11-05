package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.chat.ChatMessage;
import com.eecs3311.productfinder.domain.chat.ChatState;
import com.eecs3311.productfinder.domain.chat.Requirements;

import java.util.List;

public record ChatFlowResponse(
        ChatState state,
        List<ChatMessage> history,
        Requirements requirements,
        String followUpQuestion,
        ProductMatch match,
        String noMatchMessage,
        List<String> constraintsToRelax
) {

    public ChatFlowResponse {
        history = List.copyOf(history);
        constraintsToRelax = List.copyOf(constraintsToRelax);
    }
}
