package com.zjkl.common.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * WebSocket 配置
 * 对应 application.yml 中 websocket.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    /** 允许的 WebSocket 源地址列表（默认：http://localhost:5173） */
    private List<String> allowedOrigins = List.of("http://localhost:5173");

    /** 单条 WebSocket/STOMP 消息大小上限（字节） */
    @Min(1024)
    private int messageSizeLimit = 65_536;

    /** WebSocket 发送缓冲区大小上限（字节） */
    @Min(1024)
    private int sendBufferSizeLimit = 524_288;

}
