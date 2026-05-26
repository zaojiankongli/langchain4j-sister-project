package com.zjkl.common.config;

import com.zjkl.common.config.properties.CorsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsProperties.getAllowedOrigins().split(",");
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("New-Access-Token")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("CORS 配置已加载，允许来自 {} 的跨域请求", corsProperties.getAllowedOrigins());
    }
}
