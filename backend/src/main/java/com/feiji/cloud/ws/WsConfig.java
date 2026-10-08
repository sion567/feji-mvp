package com.feiji.cloud.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    private final FrontendWebSocketHandler frontendWebSocketHandler;

    public WsConfig(FrontendWebSocketHandler frontendWebSocketHandler) {
        this.frontendWebSocketHandler = frontendWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TODO 上线前: 收紧 AllowedOrigins 并在握手时校验登录态（携带 token 查询参数）
        registry.addHandler(frontendWebSocketHandler, "/ws/v1/messages")
                .setAllowedOriginPatterns("*");
    }
}
