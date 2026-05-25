package com.zjkl.ai.summary.controller;

import com.zjkl.ai.summary.scheduler.DailySummaryScheduler;
import com.zjkl.user.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static com.zjkl.ai.summary.config.RedisStreamConfig.*;

/**
 * 队列监控接口
 * 
 * 提供 Redis Stream 队列状态的监控能力
 */
@RestController
@RequestMapping("/api/admin/queue")
@RequiredArgsConstructor
@Slf4j
public class QueueMonitorController {
    
    private final StringRedisTemplate redisTemplate;
    private final DailySummaryScheduler dailySummaryScheduler;
    
    /**
     * 获取队列统计信息
     * 
     * @return 队列状态 Map
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取摘要流长度
        Long summaryStreamSize = redisTemplate.opsForStream().size(SUMMARY_STREAM);
        
        // 获取图片流长度
        Long imageStreamSize = redisTemplate.opsForStream().size(IMAGE_STREAM);
        
        stats.put("summaryStreamPending", summaryStreamSize != null ? summaryStreamSize : 0);
        stats.put("imageStreamPending", imageStreamSize != null ? imageStreamSize : 0);
        
        // 获取 Pending List 信息（已消费但未确认的消息）
        try {
            var summaryPending = redisTemplate.opsForStream().pending(SUMMARY_STREAM, SUMMARY_GROUP);
            var imagePending = redisTemplate.opsForStream().pending(IMAGE_STREAM, IMAGE_GROUP);
            
            stats.put("summaryPendingCount", summaryPending != null ? summaryPending.getTotalPendingMessages() : 0);
            stats.put("imagePendingCount", imagePending != null ? imagePending.getTotalPendingMessages() : 0);
            
            log.info("队列状态 - 摘要待处理：{}, 图片待处理：{}, 摘要 Pending: {}, 图片 Pending: {}", 
                stats.get("summaryStreamPending"), stats.get("imageStreamPending"),
                stats.get("summaryPendingCount"), stats.get("imagePendingCount"));
            
        } catch (Exception e) {
            log.warn("获取 Pending List 失败：{}", e.getMessage());
            stats.put("summaryPendingCount", 0);
            stats.put("imagePendingCount", 0);
        }
        
        return Result.success(stats);
    }
    
    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 检查 Redis 连接
            redisTemplate.opsForValue().get("health:check");
            
            health.put("status", "UP");
            health.put("redis", "connected");
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("redis", "disconnected");
            health.put("error", e.getMessage());
        }
        
        return Result.success(health);
    }

    @GetMapping("/h")
    public Result<Void> h() {
        dailySummaryScheduler.generateDailySummary();
        return Result.success();
    }


}
