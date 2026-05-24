package com.zjkl.ai.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 */
@Configuration
@RequiredArgsConstructor
public class OssClientConfig {
    
    private final OssConfig ossConfig;
    
    /**
     * 创建 OSS 客户端 Bean
     * 
     * @return OSS 客户端实例
     */
    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(
            ossConfig.getEndpoint(),
            ossConfig.getAccessKeyId(),
            ossConfig.getAccessKeySecret()
        );
    }
}
