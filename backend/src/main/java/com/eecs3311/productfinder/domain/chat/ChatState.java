package com.eecs3311.productfinder.domain.chat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ChatState {
    GATHERING,
    CONFIRMING,
    SEARCHING,
    PRESENTING;

    private static final Map<ChatState, Set<ChatState>> TRANSITIONS = Map.of(
            GATHERING, EnumSet.of(CONFIRMING),
            CONFIRMING, EnumSet.of(GATHERING, SEARCHING),
            SEARCHING, EnumSet.of(GATHERING, PRESENTING),
            PRESENTING, EnumSet.of(GATHERING)
    );

    public boolean canTransitionTo(ChatState target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public void requireTransitionTo(ChatState target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition chat from " + this + " to " + target);
        }
    }
}
