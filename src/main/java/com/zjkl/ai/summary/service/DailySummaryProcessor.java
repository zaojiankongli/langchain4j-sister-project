package com.zjkl.ai.summary.service;

import com.zjkl.ai.summary.agent.DailySummaryWorkflow;
import com.zjkl.ai.summary.domain.DailySummaryResult;
import com.zjkl.ai.summary.domain.task.ImageGenerationTask;
import com.zjkl.memory.service.SummaryMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_STREAM;

/**
 * 每日摘要处理服务
 * <p>
 * 纯业务逻辑层，负责在任务被认领后调用 LLM 生成摘要、保存到向量库、创建图片生成任务。
 * 不包含任何 Redis Stream 基础设施（锁、ACK、DLQ 等）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailySummaryProcessor {

    private final DailySummaryWorkflow dailySummaryWorkflow;
    private final SummaryMemoryService summaryMemoryService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 处理摘要生成任务
     *
     * @param taskId           任务 ID
     * @param userId           用户 ID
     * @param conversationText 对话文本
     * @param previousSummary  前一次摘要（可能为空）
     * @param createdAt        任务创建时间字符串（ISO 格式）
     */
    public void processTask(String taskId, String userId, String conversationText,
                            String previousSummary, String createdAt) {
        log.info("开始生成摘要：taskId={}, userId={}", taskId, userId);

        // 1. 调用 LLM 生成摘要
        DailySummaryResult result = dailySummaryWorkflow.generateDailySummary(
                conversationText,
                previousSummary
        );
        log.info("摘要生成完成：userId={}, title={}", userId, result.title());

        // 2. 保存摘要到 Milvus 向量库
        try {
            summaryMemoryService.saveToVectorStore(userId, result.title(), result.summary());
            log.info("摘要已存入向量数据库：userId={}, title={}", userId, result.title());
        } catch (Exception e) {
            log.error("摘要存入向量数据库失败（不影响后续流程）：userId={}", userId, e);
        }

        // 3. 创建并发送图片生成任务
        ImageGenerationTask imageTask = ImageGenerationTask.builder()
                .taskId(UUID.randomUUID().toString())
                .userId(userId)
                .title(result.title())
                .summary(result.summary())
                .memoryDate(LocalDate.now())
                .createdAt(LocalDateTime.parse(createdAt))
                .build();

        sendImageTask(imageTask);

        log.info("摘要任务完成，图片任务已发送：taskId={}", imageTask.getTaskId());
    }

    /**
     * 发送图片生成任务到 Redis Stream
     */
    private void sendImageTask(ImageGenerationTask task) {
        log.debug("发送图片任务到 {}: taskId={}", IMAGE_STREAM, task.getTaskId());

        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("taskId", task.getTaskId());
        messageBody.put("userId", task.getUserId());
        messageBody.put("title", task.getTitle());
        messageBody.put("summary", task.getSummary());
        messageBody.put("memoryDate", task.getMemoryDate().toString());
        messageBody.put("createdAt", task.getCreatedAt().toString());

        redisTemplate.opsForStream().add(IMAGE_STREAM, messageBody);

        log.info("图片任务已发送：taskId={}", task.getTaskId());
    }
}
