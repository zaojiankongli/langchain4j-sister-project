package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * WebSocket 配置
 * 对应 application.yml 中 websocket.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    /** 允许的 WebSocket 源地址列表（默认：http://localhost:5173） */
    private List<String> allowedOrigins = List.of("http://localhost:5173");

}
