package com.zjkl.ai.summary.config;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream 消费者组配置
 * 
 * 负责初始化和管理所有 Redis Stream 消费者组
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisStreamConfig {
    
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 摘要任务流
     */
    public static final String SUMMARY_STREAM = "summary_stream";
    
    /**
     * 图片任务流
     */
    public static final String IMAGE_STREAM = "image_stream";
    
    /**
     * 摘要消费者组
     */
    public static final String SUMMARY_GROUP = "summary-consumer-group";
    
    /**
     * 图片消费者组
     */
    public static final String IMAGE_GROUP = "image-consumer-group";

    /**
     * 应用启动时初始化消费者组
     */
    @PostConstruct
    public void initConsumerGroups() {
        log.info("开始初始化 Redis Stream 消费者组...");
        
        createGroupIfNotExists(SUMMARY_STREAM, SUMMARY_GROUP);
        createGroupIfNotExists(IMAGE_STREAM, IMAGE_GROUP);
        
        log.info("Redis Stream 消费者组初始化完成");
        log.info("  - 摘要流：{} (消费者组：{})", SUMMARY_STREAM, SUMMARY_GROUP);
        log.info("  - 图片流：{} (消费者组：{})", IMAGE_STREAM, IMAGE_GROUP);
    }
    
    /**
     * 创建消费者组（如果不存在）
     * 
     * @param streamKey Stream 键名
     * @param groupName 消费者组名称
     */
    private void createGroupIfNotExists(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, groupName);
            log.info("消费者组创建成功：{} for {}", groupName, streamKey);
        } catch (Exception e) {
            // 组已存在，忽略（Redis 会抛出 RedisException）
            log.debug("消费者组已存在：{} for {}", groupName, streamKey);
        }
    }
    
}
