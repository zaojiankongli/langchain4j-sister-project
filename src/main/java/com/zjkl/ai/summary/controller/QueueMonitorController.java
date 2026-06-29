package com.zjkl.ai.summary.controller;

import com.zjkl.ai.summary.scheduler.DailySummaryScheduler;
import com.zjkl.common.Result;
import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_GROUP;
import static com.zjkl.ai.summary.config.RedisStreamConfig.IMAGE_STREAM;
import static com.zjkl.ai.summary.config.RedisStreamConfig.SUMMARY_GROUP;
import static com.zjkl.ai.summary.config.RedisStreamConfig.SUMMARY_STREAM;

/**
 * 队列监控接口。
 * 提供 Redis Stream 队列状态的监控能力。
 */
@RestController
@RequestMapping("/api/admin/queue")
@Slf4j
public class QueueMonitorController {

    private final StringRedisTemplate redisTemplate;
    private final DailySummaryScheduler dailySummaryScheduler;
    private final UserContext userContext;
    private final AuthProperties authProperties;
    private final EndpointMetrics endpointMetrics;

    public QueueMonitorController(StringRedisTemplate redisTemplate, DailySummaryScheduler dailySummaryScheduler,
                                  UserContext userContext, AuthProperties authProperties,
                                  EndpointMetrics endpointMetrics) {
        this.redisTemplate = redisTemplate;
        this.dailySummaryScheduler = dailySummaryScheduler;
        this.userContext = userContext;
        this.authProperties = authProperties;
        this.endpointMetrics = endpointMetrics;
    }

    /**
     * 获取队列统计信息。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getQueueStats() {
        return endpointMetrics.recordResult("admin", "queue.stats", () -> {
            String authError = userContext.checkAdminAccess(authProperties);
            if (authError != null) {
                return Result.unauthorized(authError);
            }

            Map<String, Object> stats = new HashMap<>();
            Long summaryStreamSize = redisTemplate.opsForStream().size(SUMMARY_STREAM);
            Long imageStreamSize = redisTemplate.opsForStream().size(IMAGE_STREAM);

            stats.put("summaryStreamPending", summaryStreamSize != null ? summaryStreamSize : 0);
            stats.put("imageStreamPending", imageStreamSize != null ? imageStreamSize : 0);

            try {
                var summaryPending = redisTemplate.opsForStream().pending(SUMMARY_STREAM, SUMMARY_GROUP);
                var imagePending = redisTemplate.opsForStream().pending(IMAGE_STREAM, IMAGE_GROUP);

                stats.put("summaryPendingCount", summaryPending != null ? summaryPending.getTotalPendingMessages() : 0);
                stats.put("imagePendingCount", imagePending != null ? imagePending.getTotalPendingMessages() : 0);

                log.info("队列状态 - 摘要待处理: {}, 图片待处理: {}, 摘要 Pending: {}, 图片 Pending: {}",
                        stats.get("summaryStreamPending"), stats.get("imageStreamPending"),
                        stats.get("summaryPendingCount"), stats.get("imagePendingCount"));
            } catch (Exception e) {
                log.warn("获取 Pending List 失败: {}", e.getMessage());
                stats.put("summaryPendingCount", 0);
                stats.put("imagePendingCount", 0);
            }

            return Result.success(stats);
        });
    }

    /**
     * 健康检查接口。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        return endpointMetrics.recordResult("admin", "queue.health", () -> {
            String authError = userContext.checkAdminAccess(authProperties);
            if (authError != null) {
                return Result.unauthorized(authError);
            }

            Map<String, Object> health = new HashMap<>();
            try {
                redisTemplate.opsForValue().get("health:check");
                health.put("status", "UP");
                health.put("redis", "connected");
            } catch (Exception e) {
                log.warn("Health check 失败", e);
                health.put("status", "DOWN");
                health.put("redis", "disconnected");
                health.put("error", "connection_failed");
            }

            return Result.success(health);
        });
    }
}

