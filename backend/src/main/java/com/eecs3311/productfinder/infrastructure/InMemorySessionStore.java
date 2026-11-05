package com.eecs3311.productfinder.infrastructure;

import com.eecs3311.productfinder.application.SessionStore;
import com.eecs3311.productfinder.domain.chat.ChatSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentMap<Long, ChatSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<ChatSession> load(long userId) {
        return Optional.ofNullable(sessions.get(userId));
    }

    @Override
    public ChatSession getOrCreate(long userId) {
        return sessions.computeIfAbsent(userId, ChatSession::start);
    }

    @Override
    public ChatSession save(ChatSession session) {
        sessions.put(session.getUserId(), session);
        return session;
    }
}
