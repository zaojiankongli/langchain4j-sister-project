package com.zjkl.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
     * 创建请求工厂，配置超时时间（可通过 application.yml 中 app.rest.* 覆盖）
     */
    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutSeconds * 1000);
        factory.setReadTimeout(readTimeoutSeconds * 1000);
        return factory;
    }
}
