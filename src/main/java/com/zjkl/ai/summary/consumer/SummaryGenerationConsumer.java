package com.zjkl.ai.summary.consumer;

import com.zjkl.ai.summary.service.DailySummaryProcessor;
import com.zjkl.common.stream.AbstractStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zjkl.ai.summary.config.RedisStreamConfig.*;

/**
 * 摘要生成消费者
 * 
 * 从 summary_stream 消费消息，调用 LLM 生成摘要，然后发送图片生成任务到 image_stream
 */
@Service
@Slf4j
public class SummaryGenerationConsumer extends AbstractStreamConsumer {

    private static final String CONSUMER_NAME = "summary-consumer-1";
    private static final String PROCESSED_KEY_PREFIX = "daily-summary:processed:";
    private static final long DEAD_LETTER_STREAM_MAX_LENGTH = 1_000;

    private final DailySummaryProcessor dailySummaryProcessor;
    private final RedissonClient redissonClient;

    public SummaryGenerationConsumer(StringRedisTemplate redisTemplate,
                                     DailySummaryProcessor dailySummaryProcessor,
                                     RedissonClient redissonClient) {
        super(redisTemplate);
        this.dailySummaryProcessor = dailySummaryProcessor;
        this.redissonClient = redissonClient;
    }

    @Override
    protected String getStreamKey() { return SUMMARY_STREAM; }

    @Override
    protected String getConsumerGroup() { return SUMMARY_GROUP; }

    @Override
    protected String getConsumerName() { return CONSUMER_NAME; }

    @Override
    protected String getLogPrefix() { return "摘要"; }

    @Override
    protected void processMessage(MapRecord<String, Object, Object> record) {
        String taskId = valToString(record.getValue().get("taskId"));
        String userId = valToString(record.getValue().get("userId"));

        // 幂等性检查
        String processedKey = PROCESSED_KEY_PREFIX + LocalDate.now();
        Boolean isProcessed = redisTemplate.opsForSet().isMember(processedKey, taskId);

        if (Boolean.TRUE.equals(isProcessed)) {
            log.info("任务已处理，跳过：taskId={}", taskId);
            acknowledge(record);
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
                acknowledge(record);
                return;
            }

            String conversationText = valToString(record.getValue().get("conversationText"));
            String previousSummary = valToString(record.getValue().get("previousSummary"));
            String createdAt = valToString(record.getValue().get("createdAt"));

            dailySummaryProcessor.processTask(taskId, userId, conversationText, previousSummary, createdAt);

            // 标记已处理（先标记再 ACK，防止 ACK 后处理标记丢失）
            redisTemplate.opsForSet().add(processedKey, taskId);
            redisTemplate.expire(processedKey, 24, TimeUnit.HOURS);

            acknowledge(record);
            log.info("摘要任务完成：taskId={}", taskId);

        } catch (Exception e) {
            log.error("摘要生成失败：taskId={}, userId={}", taskId, userId, e);
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
                deadLetter.put("stackTrace", stackTraceOf(e));
                deadLetter.put("retryCount", retryCount);
                redisTemplate.opsForStream().add("daily-summary:dead-letter", deadLetter);
                redisTemplate.opsForStream().trim("daily-summary:dead-letter", DEAD_LETTER_STREAM_MAX_LENGTH, true);
                acknowledge(record);
            } else {
                // 重试 < 3 次，抛出异常让基类统一处理（不 ACK）
                // 注意：finally 中会释放锁，重试期间其他消费者可能获取锁，
                // 但 processedKey 未写入，重复处理是安全的（幂等性检查会在 processTask 前确保幂等）
                throw e;
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static String valToString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String stackTraceOf(Exception exception) {
        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
