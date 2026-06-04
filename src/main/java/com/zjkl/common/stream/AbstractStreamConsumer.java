package com.zjkl.common.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream 消费者抽象基类
 * <p>
 * 提供统一的消费者循环 + 优雅关闭基础设施。
 * 子类只需实现：
 * <ul>
 *   <li>{@link #getStreamKey()} — 流名称</li>
 *   <li>{@link #getConsumerGroup()} — 消费者组</li>
 *   <li>{@link #getConsumerName()} — 消费者名称</li>
 *   <li>{@link #getLogPrefix()} — 日志前缀</li>
 *   <li>{@link #processMessage(MapRecord)} — 消息处理逻辑</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractStreamConsumer {

    private static final long PENDING_RECOVERY_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final long PENDING_READ_COUNT = 10;

    protected final StringRedisTemplate redisTemplate;

    /** 运行状态标志（用于优雅关闭） */
    protected final AtomicBoolean running = new AtomicBoolean(true);

    /** 消费者线程引用（用于中断） */
    private Thread consumerThread;

    private long lastPendingRecoveryAt;

    protected AbstractStreamConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ========== 子类配置 ==========

    /** Redis Stream 名称 */
    protected abstract String getStreamKey();

    /** 消费者组名称 */
    protected abstract String getConsumerGroup();

    /** 消费者名称 */
    protected abstract String getConsumerName();

    /** 日志前缀（如 "图片"、"摘要"） */
    protected abstract String getLogPrefix();

    /**
     * 处理单条消息
     * <p>
     * 注意：子类实现应保证幂等性。同一条消息可能因 Redis Stream 的 pending 恢复机制
     * 被重复投递，子类应确保重复处理不会产生副作用（如重复写入、重复通知等）。
     */
    protected abstract void processMessage(MapRecord<String, Object, Object> record);

    // ========== 生命周期 ==========

    @PostConstruct
    public void startConsumer() {
        consumerThread = Thread.ofVirtual()
                .name(getLogPrefix() + "-consumer-", 0)
                .start(this::consumeLoop);
        log.info("{}生成消费者已启动（虚拟线程）", getLogPrefix());
    }

    @PreDestroy
    public void shutdown() {
        log.info("开始关闭{}生成消费者...", getLogPrefix());
        running.set(false);

        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(10000);
                log.info("{}生成消费者已关闭", getLogPrefix());
            } catch (InterruptedException e) {
                log.error("等待{}消费者线程关闭超时", getLogPrefix(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== 消费循环 ==========

    private void consumeLoop() {
        log.info("开始消费{}任务流：{}", getLogPrefix(), getStreamKey());

        while (running.get()) {
            try {
                recoverPendingMessagesIfDue();

                List<MapRecord<String, Object, Object>> messages =
                    redisTemplate.opsForStream().read(
                        Consumer.from(getConsumerGroup(), getConsumerName()),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                        StreamOffset.create(getStreamKey(), ReadOffset.lastConsumed())
                    );

                if (messages != null && !messages.isEmpty()) {
                    log.debug("收到 {} 条{}任务", messages.size(), getLogPrefix());

                    for (MapRecord<String, Object, Object> message : messages) {
                        if (!running.get()) {
                            log.info("检测到关闭信号，停止处理消息");
                            break;
                        }
                        processMessage(message);
                    }
                }
            } catch (Exception e) {
                if (!running.get()) {
                    log.info("{}消费者已停止", getLogPrefix());
                    break;
                }
                log.error("消费{}任务失败", getLogPrefix(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    log.info("{}消费者被中断", getLogPrefix());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("{}消费者循环已退出", getLogPrefix());
    }

    private void recoverPendingMessagesIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPendingRecoveryAt < PENDING_RECOVERY_INTERVAL_MS) {
            return;
        }
        lastPendingRecoveryAt = now;

        List<MapRecord<String, Object, Object>> pendingMessages = redisTemplate.opsForStream().read(
                Consumer.from(getConsumerGroup(), getConsumerName()),
                StreamReadOptions.empty().count(PENDING_READ_COUNT),
                StreamOffset.create(getStreamKey(), ReadOffset.from("0"))
        );

        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        log.info("发现 {} 条{}pending任务，开始恢复处理", pendingMessages.size(), getLogPrefix());
        for (MapRecord<String, Object, Object> message : pendingMessages) {
            if (!running.get()) {
                break;
            }
            processMessage(message);
        }
    }

    /**
     * ACK 确认消息
     */
    protected void acknowledge(MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(getConsumerGroup(), record);
    }

    /**
     * ACK 确认消息（按 recordId）
     */
    protected void acknowledge(String recordId) {
        redisTemplate.opsForStream().acknowledge(getStreamKey(), getConsumerGroup(), recordId);
    }
}
