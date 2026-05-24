package com.zjkl.common.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 */
@Configuration
@Slf4j
public class RedissonConfig {
    
    @Value("${spring.data.redis.host}")
    private String host;
    
    @Value("${spring.data.redis.port}")
    private int port;
    
    @Value("${spring.data.redis.password:}")
    private String password;
    
    @Value("${spring.data.redis.database:0}")
    private int database;
    
    /**
     * 创建 Redisson 客户端
     *
     */
    @Bean
    public RedissonClient redissonClient() {
        log.info("初始化 Redisson 客户端 - host={}:{}", host, port);
        
        Config config = new Config();
        var singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + host + ":" + port);
        singleServerConfig.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            singleServerConfig.setPassword(password);
        }
        singleServerConfig.setConnectionMinimumIdleSize(10);
        singleServerConfig.setConnectionPoolSize(64);
        
        RedissonClient redissonClient = Redisson.create(config);
        
        log.info("Redisson 客户端初始化完成");
        
        return redissonClient;
    }
}
