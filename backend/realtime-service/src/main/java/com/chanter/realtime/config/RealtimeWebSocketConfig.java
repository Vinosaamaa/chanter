package com.chanter.realtime.config;

import com.chanter.realtime.websocket.RealtimeWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class RealtimeWebSocketConfig {

    @Bean
    HandlerMapping realtimeWebSocketMapping(RealtimeWebSocketHandler handler) {
        Map<String, WebSocketHandler> urlMap = Map.of("/api/v1/realtime/ws", handler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(urlMap);
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
