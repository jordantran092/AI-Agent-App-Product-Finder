package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.chat.ChatSession;

import java.util.Optional;

public interface SessionStore {

    Optional<ChatSession> load(long userId);

    ChatSession save(ChatSession session);
}
