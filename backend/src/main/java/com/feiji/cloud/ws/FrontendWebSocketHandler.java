package com.feiji.cloud.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class FrontendWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FrontendWebSocketHandler.class);

    private final WsSessionManager sessionManager;

    public FrontendWebSocketHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
        log.info("WebSocket 连接建立: {}, 当前在线 {}", session.getId(), sessionManager.count());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // TODO 二期: 处理前端订阅/心跳消息
        log.debug("收到前端消息: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
        log.info("WebSocket 连接关闭: {}, status={}", session.getId(), status);
    }
}
