package com.zjkl.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient 配置类
 */
@Configuration
public class RestClientConfig {

    @Value("${app.rest.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${app.rest.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(createRequestFactory())
                .build();
    }

    /**
     * 创建请求工厂，使用 JdkClientHttpRequestFactory 以支持完整的 HTTP/2 和超时控制。
     * connectTimeout 在 HttpClient 级别设置；readTimeout 在请求级别设置。
     */
    private ClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
