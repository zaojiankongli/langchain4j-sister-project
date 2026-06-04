package com.zjkl.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 线程池配置
 * 对应 application.yml 中 app.thread-pool.* 的配置项
 * <p>
 * 不同核心数的服务器可通过配置文件调整，推荐值（2核 / 4核 / 8核）：
 * <ul>
 *   <li>websocket-sender: 2 / 4 / 8</li>
 *   <li>async-core: 2 / 4 / 8</li>
 *   <li>async-max: 4 / 8 / 16</li>
 *   <li>scheduling: 2 / 4 / 6</li>
 * </ul>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.thread-pool")
public class ThreadPoolProperties {

    // ==================== WebSocket 消息发送线程池 ====================

    /** WebSocket 发送调度线程池核心大小（默认：2） */
    @Positive
    @Max(16)
    private int websocketSenderCoreSize = 2;

    /** WebSocket 发送调度线程池最大大小（默认：4） */
    @Positive
    @Max(32)
    private int websocketSenderMaxSize = 4;

    /** WebSocket 发送队列容量（默认：100） */
    @Positive
    @Max(10_000)
    private int websocketSenderQueueCapacity = 100;

    // ==================== @Async 异步任务线程池 ====================

    /** 异步任务线程池核心大小（默认：2） */
    @Positive
    @Max(16)
    private int asyncCorePoolSize = 2;

    /** 异步任务线程池最大大小（默认：4） */
    @Positive
    @Max(32)
    private int asyncMaxPoolSize = 4;

    /** 异步任务队列容量（默认：100） */
    @Positive
    @Max(10_000)
    private int asyncQueueCapacity = 100;

    /** 异步任务线程名前缀（默认：async-task-） */
    @NotBlank
    private String asyncThreadNamePrefix = "async-task-";

    // ==================== Redisson 连接池 ====================

    /** Redisson 最小空闲连接数（默认：2，适合 2 核 4G） */
    @Positive
    @Max(16)
    private int redissonMinIdle = 2;

    /** Redisson 连接池大小（默认：8，适合 2 核 4G） */
    @Positive
    @Max(64)
    private int redissonPoolSize = 8;
}
