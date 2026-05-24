package com.zjkl.common.config;

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

    /**
     * 连接超时时间（秒）
     */
    private static final int CONNECT_TIMEOUT = 10;

    /**
     * 读取超时时间（秒）
     */
    private static final int READ_TIMEOUT = 30;


    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(createRequestFactory())
                .build();
    }

    /**
     * 创建请求工厂，配置超时时间
     */
    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT * 1000);
        factory.setReadTimeout(READ_TIMEOUT * 1000);
        return factory;
    }
}
