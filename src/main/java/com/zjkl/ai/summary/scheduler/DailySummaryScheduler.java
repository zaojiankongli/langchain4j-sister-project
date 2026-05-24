package com.zjkl.ai.summary.scheduler;


import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.memory.store.RedisChatMemoryStore;
import com.zjkl.ai.summary.domain.task.SummaryGenerationTask;
import com.zjkl.ai.util.ChatMessageUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.zjkl.ai.summary.config.RedisStreamConfig.SUMMARY_STREAM;

/**
 * 每日摘要定时任务 - 只负责发送消息到 Redis Stream
 * 
 * 每天凌晨 0 点执行，获取活跃用户，发送摘要生成任务到消息队列
 * 由后台消费者异步处理
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailySummaryScheduler {
    
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 每天凌晨 0 点执行
     * 
     * cron 表达式：秒 分 时 日 月 周
     * 0 0 0 * * ? = 每天 00:00:00 执行
     */
   /* @Scheduled(cron = "0/60 * * * * ?")*/
    @Scheduled(cron = "0 0 0 * * ?")
    public void generateDailySummary() {
        log.info("========== 开始执行每日摘要生成任务 ==========");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 获取今日活跃用户
            Set<String> userIds = userActivityTracker.getActiveMemoryIdsInLastDays(1);
            log.info("今日活跃用户数：{}", userIds.size());
            
            int successCount = 0;
            int failCount = 0;
            
            // 2. 对每个用户发送摘要任务到消息队列
            for (String userId : userIds) {
                try {
                    processUserMemory(userId);
                    successCount++;
                } catch (Exception e) {
                    log.error("用户 {} 的任务发送失败", userId, e);
                    failCount++;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("========== 主线程任务完成 - 成功：{}, 失败：{}, 耗时：{}ms ==========", 
                successCount, failCount, duration);
            
        } catch (Exception e) {
            log.error("每日摘要生成任务执行失败", e);
        }
    }
    
    /**
     * 处理单个用户的每日摘要 - 只发送消息，不阻塞
     * 
     * @param userId 用户 ID
     */
    public void processUserMemory(String userId) {
        log.debug("开始处理用户 {} 的每日摘要", userId);
        
        // 1. 从 Redis 获取对话历史
        List<ChatMessage> messages = redisChatMemoryStore.getMessages(userId);
        
        if (messages == null || messages.isEmpty()) {
            log.debug("用户 {} 没有对话历史，跳过", userId);
            return;
        }
        
        // 2. 过滤掉系统消息（摘要是基于用户对话）
        List<ChatMessage> conversationMessages = messages.stream()
                .filter(msg -> !(msg instanceof SystemMessage))
                .collect(Collectors.toList());
        
        if (conversationMessages.isEmpty()) {
            log.debug("用户 {} 没有有效对话内容，跳过", userId);
            return;
        }
        
        // 3. 转换为文本（使用工具类）
        String conversationText = ChatMessageUtils.messagesToText(conversationMessages);
        
        // 4. 获取旧摘要（如果没有则为空串，AI 能处理）
        String previousSummary = getPreviousSummary(userId);
        
        // 5. 组装摘要生成任务
        SummaryGenerationTask task = SummaryGenerationTask.builder()
            .taskId(UUID.randomUUID().toString())
            .userId(userId)
            .conversationText(conversationText)
            .previousSummary(previousSummary)
            .createdAt(LocalDateTime.now().minusDays(1))
            .build();
        
        // 6. 发送到 Redis Stream（不阻塞！）
        sendSummaryTask(task);
        
        log.info("用户 {} 的摘要任务已发送，taskId={}", userId, task.getTaskId());
    }
    
    /**
     * 发送摘要生成任务到 Redis Stream
     * 
     * @param task 摘要生成任务
     */
    private void sendSummaryTask(SummaryGenerationTask task) {
        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("taskId", task.getTaskId());
        messageBody.put("userId", task.getUserId());
        messageBody.put("conversationText", task.getConversationText());
        messageBody.put("previousSummary", task.getPreviousSummary());
        messageBody.put("createdAt", task.getCreatedAt().toString());
        
        redisTemplate.opsForStream().add(SUMMARY_STREAM, messageBody);
    }
    
    /**
     * 从 Redis 获取旧摘要
     * 
     * @param userId 用户 ID
     * @return 旧摘要，如果没有则返回空串（不是 null，避免 AI 困惑）
     */
    private String getPreviousSummary(String userId) {
        String summary = redisTemplate.opsForValue().get("chat:summary:" + userId);
        return summary != null ? summary : "";
    }
}
