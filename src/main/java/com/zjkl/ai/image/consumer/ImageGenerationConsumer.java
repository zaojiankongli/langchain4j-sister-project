package com.zjkl.ai.image.consumer;

import com.zjkl.ai.image.service.MemoryImageGenerator;
import com.zjkl.common.stream.AbstractStreamConsumer;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.user.domain.ConversationMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_GROUP;
import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_STREAM;

/**
 * 图片生成消费者
 * 
 * 从 image_stream 消费消息，异步生成图片，回调完成后入库
 * 采用异步回调模式，不阻塞消费者线程
 */
@Service
@Slf4j
public class ImageGenerationConsumer extends AbstractStreamConsumer {

    private static final String CONSUMER_NAME = "image-consumer-1";
    private static final int MAX_IN_FLIGHT_IMAGE_TASKS = 2;

    private final MemoryImageGenerator memoryImageGenerator;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final Semaphore inFlightImageTasks = new Semaphore(MAX_IN_FLIGHT_IMAGE_TASKS);

    public ImageGenerationConsumer(StringRedisTemplate redisTemplate,
                                   MemoryImageGenerator memoryImageGenerator,
                                   ConversationMemoryMapper conversationMemoryMapper) {
        super(redisTemplate);
        this.memoryImageGenerator = memoryImageGenerator;
        this.conversationMemoryMapper = conversationMemoryMapper;
    }

    @Override
    protected String getStreamKey() { return IMAGE_STREAM; }

    @Override
    protected String getConsumerGroup() { return IMAGE_GROUP; }

    @Override
    protected String getConsumerName() { return CONSUMER_NAME; }

    @Override
    protected String getLogPrefix() { return "图片"; }

    @Override
    protected void processMessage(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        Object taskIdObj = value.get("taskId");
        String taskId = taskIdObj != null ? taskIdObj.toString() : null;
        String userId = valToString(value.get("userId"));
        String title = valToString(value.get("title"));
        String summary = valToString(value.get("summary"));
        String memoryDateStr = valToString(value.get("memoryDate"));
        String createdAtStr = valToString(value.get("createdAt"));

        log.info("开始处理图片任务：taskId={}, userId={}", taskId, userId);

        try {
            LocalDate memoryDate = LocalDate.parse(memoryDateStr);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);

            acquireImagePermit(taskId);

            log.info("触发异步图片生成：userId={}, date={}", userId, memoryDate);
            String recordId = record.getId().getValue();
            CompletableFuture<String> imageFuture;
            try {
                imageFuture = memoryImageGenerator.generateImageAsync(userId, title, summary, memoryDate);
            } catch (Exception e) {
                inFlightImageTasks.release();
                log.error("图片生成异步调用失败：taskId={}, userId={}", taskId, userId, e);
                acknowledge(recordId);
                return;
            }

            imageFuture.whenComplete((imageUrl, throwable) -> {
                try {
                    if (throwable != null) {
                        log.error("图片生成最终失败（含降级），记忆未入库：taskId={}, userId={}", taskId, userId, throwable);
                        return;
                    }
                    if (!running.get()) {
                        log.warn("服务关闭中，跳过图片入库：taskId={}", taskId);
                        return;
                    }

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
                } finally {
                    inFlightImageTasks.release();
                }

                acknowledge(recordId);
                log.debug("图片任务确认完成：taskId={}, recordId={}", taskId, recordId);
            });

            log.info("图片任务已触发，消费者线程可处理下一个任务：taskId={}", taskId);

        } catch (Exception e) {
            log.error("图片任务处理失败：taskId={}, userId={}", taskId, userId, e);
            acknowledge(record);
        }
    }

    private static String valToString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private void acquireImagePermit(String taskId) {
        try {
            inFlightImageTasks.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片任务等待执行被中断: taskId=" + taskId, e);
        }
    }
}
