package com.zjkl.common.config;

import com.zjkl.common.config.properties.RedisProperties;
import com.zjkl.common.config.properties.ThreadPoolProperties;
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
public class RedissonConfig {

    private final RedisProperties redisProperties;
    private final ThreadPoolProperties threadPoolProperties;

    public RedissonConfig(RedisProperties redisProperties, ThreadPoolProperties threadPoolProperties) {
        this.redisProperties = redisProperties;
        this.threadPoolProperties = threadPoolProperties;
    }
    
    /**
     * 创建 Redisson 客户端
     * 连接池大小通过 app.thread-pool.redisson-pool-size 配置（默认 8，2 核推荐值）
     */
    @Bean
    public RedissonClient redissonClient() {
        log.debug("初始化 Redisson 客户端 - host={}:{}", redisProperties.getHost(), redisProperties.getPort());
        
        Config config = new Config();
        var singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());
        singleServerConfig.setDatabase(redisProperties.getDatabase());
        String password = redisProperties.getPassword();
        if (password != null && !password.isEmpty()) {
            singleServerConfig.setPassword(password);
        }
        int minIdle = threadPoolProperties.getRedissonMinIdle();
        int poolSize = threadPoolProperties.getRedissonPoolSize();
        singleServerConfig.setConnectionMinimumIdleSize(minIdle);
        singleServerConfig.setConnectionPoolSize(poolSize);
        log.debug("Redisson 连接池配置：minIdle={}, poolSize={}", minIdle, poolSize);
        
        RedissonClient redissonClient = Redisson.create(config);
        
        log.info("Redisson 客户端初始化完成");
        
        return redissonClient;
    }
}
