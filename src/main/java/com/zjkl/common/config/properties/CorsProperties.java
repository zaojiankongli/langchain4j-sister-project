package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置
 * 对应 application.yml 中 cors.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 允许的跨域来源（逗号分隔），默认：http://localhost:5173,http://127.0.0.1:5173 */
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

}
