package com.zjkl.common.config;

import com.zjkl.common.config.properties.RedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties redisProperties;
    
    /**
     * 创建 Redisson 客户端
     *
     */
    @Bean
    public RedissonClient redissonClient() {
        log.info("初始化 Redisson 客户端 - host={}:{}", redisProperties.getHost(), redisProperties.getPort());
        
        Config config = new Config();
        var singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());
        singleServerConfig.setDatabase(redisProperties.getDatabase());
        String password = redisProperties.getPassword();
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
