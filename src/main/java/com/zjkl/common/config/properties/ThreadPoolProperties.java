package com.zjkl.common.config.properties;

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
 *   <li>llm/tts/milvus bulkhead: 根据外部依赖吞吐调整</li>
 * </ul>
 * <p>
 * 注：@Async 异步任务已迁移至虚拟线程执行器（见 AsyncConfig），无需独立线程池配置。
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

    // ==================== 外部 API bulkhead ====================

    /** LLM 外部调用线程池核心大小（默认：2） */
    @Positive
    @Max(16)
    private int llmCoreSize = 2;

    /** LLM 外部调用线程池最大大小（默认：4） */
    @Positive
    @Max(32)
    private int llmMaxSize = 4;

    /** LLM 外部调用队列容量（默认：32） */
    @Positive
    @Max(10_000)
    private int llmQueueCapacity = 32;

    /** TTS 外部调用线程池核心大小（默认：1） */
    @Positive
    @Max(8)
    private int ttsCoreSize = 1;

    /** TTS 外部调用线程池最大大小（默认：2） */
    @Positive
    @Max(16)
    private int ttsMaxSize = 2;

    /** TTS 外部调用队列容量（默认：16） */
    @Positive
    @Max(10_000)
    private int ttsQueueCapacity = 16;

    /** Milvus 外部调用线程池核心大小（默认：1） */
    @Positive
    @Max(8)
    private int milvusCoreSize = 1;

    /** Milvus 外部调用线程池最大大小（默认：2） */
    @Positive
    @Max(16)
    private int milvusMaxSize = 2;

    /** Milvus 外部调用队列容量（默认：16） */
    @Positive
    @Max(10_000)
    private int milvusQueueCapacity = 16;

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
