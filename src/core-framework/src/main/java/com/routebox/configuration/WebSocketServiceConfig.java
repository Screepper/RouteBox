package com.routebox.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.routebox.controller.ws.RDHandler;

/**
 * @Description WebSocket Configuration
 * @Date 2025/12/15
**/
@Configuration
@EnableWebSocket
public class WebSocketServiceConfig implements WebSocketConfigurer {

    private final RDHandler rdHandler;

    public WebSocketServiceConfig(RDHandler rdHandler) {
        this.rdHandler = rdHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rdHandler, "/ws/rd").setAllowedOrigins("*");
    }
}