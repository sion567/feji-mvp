package com.feiji.cloud.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WsSessionManager.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public void broadcast(String text) {
        TextMessage message = new TextMessage(text);
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    // 同一 Session 的并发写必须串行，否则抛 ConcurrentMessageException
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("WebSocket 推送失败, session={}", session.getId());
                }
            }
        });
    }

    public int count() {
        return sessions.size();
    }
}
