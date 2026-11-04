package com.eecs3311.productfinder.domain.chat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ChatSession {

    private final UUID id;
    private final long userId;
    private final List<ChatMessage> history = new ArrayList<>();
    private ChatState state;
    private Requirements pendingRequirements;

    private ChatSession(UUID id, long userId, ChatState state) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public static ChatSession start(long userId) {
        return new ChatSession(UUID.randomUUID(), userId, ChatState.GATHERING);
    }

    public UUID getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public ChatState getState() {
        return state;
    }

    public Requirements getPendingRequirements() {
        return pendingRequirements;
    }

    public List<ChatMessage> getHistory() {
        return List.copyOf(history);
    }

    public void addMessage(MessageRole role, String content) {
        history.add(new ChatMessage(role, content, OffsetDateTime.now()));
    }

    public void setPendingRequirements(Requirements requirements) {
        requireState(ChatState.GATHERING);
        this.pendingRequirements = Objects.requireNonNull(requirements, "requirements must not be null");
    }

    public void clearPendingRequirements() {
        if (state != ChatState.GATHERING && state != ChatState.CONFIRMING) {
            throw new IllegalStateException("Pending requirements can only be cleared before search");
        }
        pendingRequirements = null;
    }

    public void requireState(ChatState expected) {
        if (state != expected) {
            throw new IllegalStateException("Expected chat state " + expected + " but was " + state);
        }
    }

    public void transitionTo(ChatState target) {
        Objects.requireNonNull(target, "target must not be null");
        state.requireTransitionTo(target);
        if (target == ChatState.CONFIRMING && pendingRequirements == null) {
            throw new IllegalStateException("Requirements must be extracted before confirmation");
        }
        if (target == ChatState.SEARCHING && pendingRequirements == null) {
            throw new IllegalStateException("Requirements must be confirmed before search");
        }
        state = target;
        if (target == ChatState.GATHERING) {
            pendingRequirements = null;
        }
    }
}
