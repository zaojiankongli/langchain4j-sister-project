package com.zjkl.ai.image.consumer;

import com.zjkl.ai.image.service.MemoryImageGenerator;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.user.domain.ConversationMemory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_GROUP;
import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_STREAM;

/**
 * 图片生成消费者
 * 
 * 从 image_stream 消费消息，异步生成图片，回调完成后入库
 * 采用异步回调模式，不阻塞消费者线程
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationConsumer {
    
    private final MemoryImageGenerator memoryImageGenerator;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 消费者名称
     */
    private static final String CONSUMER_NAME = "image-consumer-1";
    
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
        log.info("图片生成消费者已启动（虚拟线程）");
    }
    
    /**
     * 优雅关闭消费者
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭图片生成消费者...");
        running.set(false);
        
        // 中断消费者线程
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                // 等待线程结束（最多 10 秒）
                consumerThread.join(10000);
                log.info("图片生成消费者已关闭");
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
        log.info("开始消费图片任务流：{}", IMAGE_STREAM);
        
        while (running.get()) {
            try {
                // 阻塞读取，最多等待 5 秒
                List<MapRecord<String, Object, Object>> messages =
                    redisTemplate.opsForStream().read(
                        Consumer.from(IMAGE_GROUP, CONSUMER_NAME),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                        StreamOffset.create(IMAGE_STREAM, ReadOffset.lastConsumed())
                    );
                
                if (messages != null && !messages.isEmpty()) {
                    log.debug("收到 {} 条图片任务", messages.size());
                    
                    for (MapRecord<String, Object, Object> message : messages) {
                        // 检查是否正在关闭
                        if (!running.get()) {
                            log.info("检测到关闭信号，停止处理消息");
                            break;
                        }
                        
                        processImageTask(message);
                    }
                }
            } catch (Exception e) {
                // 检查是否是关闭导致的中断
                if (!running.get()) {
                    log.info("消费者已停止");
                    break;
                }
                
                log.error("消费图片任务失败", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    log.info("消费者被中断");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("图片消费者循环已退出");
    }
    
    /**
     * 处理单个图片任务
     *
     * @param record Redis Stream 消息记录
     */
    private void processImageTask(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        Object taskIdObj = value.get("taskId");
        String taskId = taskIdObj != null ? taskIdObj.toString() : null;
        String userId = String.valueOf(value.get("userId"));
        String title = String.valueOf(value.get("title"));
        String summary = String.valueOf(value.get("summary"));
        String memoryDateStr = String.valueOf(value.get("memoryDate"));
        String createdAtStr = String.valueOf(value.get("createdAt"));
        
        log.info("开始处理图片任务：taskId={}, userId={}", taskId, userId);
        
        try {
            LocalDate memoryDate = LocalDate.parse(memoryDateStr);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
            
            // ========== 异步生成图片（不阻塞消费者线程）==========
            log.info("触发异步图片生成：userId={}, date={}", userId, memoryDate);
            CompletableFuture<String> imageFuture = 
                memoryImageGenerator.generateImageAsync(userId, title, summary, memoryDate);
            
            String recordId = record.getId().getValue();
            
            // ========== 注册回调（图片完成后自动入库并ACK）==========
            imageFuture.thenAcceptAsync(imageUrl -> {
                if (!running.get()) {
                    log.warn("服务关闭中，跳过图片入库：taskId={}", taskId);
                    return;
                }
                try {
                    log.info("图片生成完成，开始入库：taskId={}, imageUrl={}", taskId, imageUrl);

                    ConversationMemory memory = ConversationMemory.builder()
                        .userId(userId)
                        .title(title)
                        .content(summary)
                        .imageUrl(imageUrl)
                        .memoryDate(memoryDate)
                        .createdAt(createdAt)
                        .build();

                    conversationMemoryMapper.insert(memory);

                    log.info("图片任务完成入库：taskId={}, userId={}, imageUrl={}",
                        taskId, userId, imageUrl);

                } catch (DuplicateKeyException e) {
                    log.info("图片已入库（唯一索引），跳过：taskId={}", taskId);
                } catch (Exception e) {
                    log.error("图片任务入库失败：taskId={}, userId={}", taskId, userId, e);
                    return;
                }

                redisTemplate.opsForStream().acknowledge(IMAGE_STREAM, IMAGE_GROUP, recordId);
                log.debug("图片任务确认完成：taskId={}, recordId={}", taskId, recordId);
            }).exceptionally(throwable -> {
                log.error("图片生成最终失败（含降级），记忆未入库：taskId={}, userId={}", taskId, userId, throwable);
                return null;
            });
            
            log.info("图片任务已触发，消费者线程可处理下一个任务：taskId={}", taskId);

        } catch (Exception e) {
            log.error("图片任务处理失败：taskId={}, userId={}", taskId, userId, e);
            // 异常时直接 ACK 防止无限重试
            redisTemplate.opsForStream().acknowledge(IMAGE_STREAM, IMAGE_GROUP, record.getId().getValue());
        }
    }
}
