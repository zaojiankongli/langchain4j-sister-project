package com.zjkl.miniprogram.realtime;

import com.zjkl.common.config.properties.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MiniprogramRealtimeWebSocketConfig implements WebSocketConfigurer {

    private final MiniprogramRealtimeWebSocketHandler handler;
    private final WebSocketProperties webSocketProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/miniprogram/realtime")
                .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins().toArray(new String[0]));
    }
}
