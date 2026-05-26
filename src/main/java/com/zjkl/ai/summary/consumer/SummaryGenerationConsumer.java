package com.zjkl.ai.summary.consumer;

import com.zjkl.ai.summary.service.DailySummaryProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zjkl.ai.summary.config.RedisStreamConfig.*;


/**
 * 摘要生成消费者
 * 
 * 从 summary_stream 消费消息，调用 LLM 生成摘要，然后发送图片生成任务到 image_stream
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryGenerationConsumer {
    
    private final DailySummaryProcessor dailySummaryProcessor;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    
    /**
     * 消费者名称
     */
    private static final String CONSUMER_NAME = "summary-consumer-1";
    
    /**
     * 幂等性 Key 前缀
     */
    private static final String PROCESSED_KEY_PREFIX = "daily-summary:processed:";
    
    /**
     * 运行状态标志（用于优雅关闭）
     */
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    /**
     * 消费者线程引用（用于中断）
     */
    private Thread consumerThread;
    
    /**
     * 启动消费者线程 - 使用 JDK 21 虚拟线程
     */
    @PostConstruct
    public void startConsumer() {
        consumerThread = Thread.startVirtualThread(this::consumeMessages);
        log.info("摘要生成消费者已启动（虚拟线程）");
    }
    
    /**
     * 优雅关闭消费者
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭摘要生成消费者...");
        running.set(false);
        
        // 中断消费者线程
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                // 等待线程结束（最多 10 秒）
                consumerThread.join(10000);
                log.info("摘要生成消费者已关闭");
            } catch (InterruptedException e) {
                log.error("等待消费者线程关闭超时", e);
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 持续消费消息（阻塞式读取）
     */
    private void consumeMessages() {
        log.info("开始消费摘要任务流：{}", SUMMARY_STREAM);
        
        while (running.get()) {
            try {
                // 阻塞读取，最多等待 5 秒
                List<MapRecord<String, Object, Object>> messages =
                    redisTemplate.opsForStream().read(
                        Consumer.from(SUMMARY_GROUP, CONSUMER_NAME),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                        StreamOffset.create(SUMMARY_STREAM, ReadOffset.lastConsumed())
                    );

                if (messages != null && !messages.isEmpty()) {
                    log.debug("收到 {} 条摘要任务", messages.size());
                    
                    for (MapRecord<String, Object, Object> message : messages) {
                        // 检查是否正在关闭
                        if (!running.get()) {
                            log.info("检测到关闭信号，停止处理消息");
                            break;
                        }
                        
                        processSummaryTask(message);
                        
                        // 成功后发送 ACK 确认
                        redisTemplate.opsForStream().acknowledge(SUMMARY_GROUP, message);
                        log.debug("摘要任务确认：{}", message.getId());
                    }
                }
            } catch (Exception e) {
                // 检查是否是关闭导致的中断
                if (!running.get()) {
                    log.info("消费者已停止");
                    break;
                }
                
                log.error("消费摘要任务失败", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    log.info("消费者被中断");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("摘要消费者循环已退出");
    }
    
    /**
     * 处理单个摘要任务 - 带幂等性检查和分布式锁
     * <p>
     * 职责：幂等性判断 → 分布式锁 → 委托 DailySummaryProcessor 执行业务 → 标记已处理 → ACK / 重试 / DLQ
     *
     * @param record Redis Stream 消息记录
     */
    private void processSummaryTask(MapRecord<String, Object, Object> record) {
        String taskId = (String) record.getValue().get("taskId");
        String userId = (String) record.getValue().get("userId");

        // 幂等性检查
        String processedKey = PROCESSED_KEY_PREFIX + LocalDate.now();
        Boolean isProcessed = redisTemplate.opsForSet().isMember(processedKey, taskId);

        if (Boolean.TRUE.equals(isProcessed)) {
            log.info("任务已处理，跳过：taskId={}", taskId);
            redisTemplate.opsForStream().acknowledge(SUMMARY_GROUP, record);
            return;
        }

        // 分布式锁
        RLock lock = redissonClient.getLock("daily-summary-lock:" + taskId);
        boolean locked = false;
        try {
            locked = lock.tryLock(1, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取摘要锁被中断：taskId={}", taskId);
            return;
        }

        if (!locked) {
            log.warn("任务正在处理中：taskId={}", taskId);
            return;  // 不 ACK，等待重试
        }

        try {
            // 双重检查
            isProcessed = redisTemplate.opsForSet().isMember(processedKey, taskId);
            if (Boolean.TRUE.equals(isProcessed)) {
                log.info("任务已处理（双重检查），跳过：taskId={}", taskId);
                redisTemplate.opsForStream().acknowledge(SUMMARY_GROUP, record);
                return;
            }

            // 提取数据并委托给业务处理器
            String conversationText = (String) record.getValue().get("conversationText");
            String previousSummary = (String) record.getValue().get("previousSummary");
            String createdAt = (String) record.getValue().get("createdAt");

            dailySummaryProcessor.processTask(taskId, userId, conversationText, previousSummary, createdAt);

            // 标记已处理
            redisTemplate.opsForSet().add(processedKey, taskId);
            redisTemplate.expire(processedKey, 24, TimeUnit.HOURS);

            // ACK
            redisTemplate.opsForStream().acknowledge(SUMMARY_GROUP, record);

            log.info("摘要任务完成：taskId={}", taskId);

        } catch (Exception e) {
            log.error("摘要生成失败：taskId={}, userId={}", taskId, userId, e);
            // 记录重试次数
            String retryKey = "daily-summary:retry:" + taskId;
            Long retryCount = redisTemplate.opsForValue().increment(retryKey);
            redisTemplate.expire(retryKey, 1, TimeUnit.DAYS);
            if (retryCount != null && retryCount >= 3) {
                log.warn("摘要任务重试次数已达上限，移入死信队列：taskId={}", taskId);
                Map<String, Object> deadLetter = new HashMap<>();
                for (Map.Entry<Object, Object> entry : record.getValue().entrySet()) {
                    deadLetter.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                deadLetter.put("error", e.getMessage());
                deadLetter.put("retryCount", retryCount);
                redisTemplate.opsForStream().add("daily-summary:dead-letter", deadLetter);
                redisTemplate.opsForStream().acknowledge(SUMMARY_GROUP, record);
            } else {
                throw e;
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
}
