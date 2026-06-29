package com.zjkl.user.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.user.service.InterestTagGenerateService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户兴趣标签控制器。
 * 认证方式：从 UserContext 获取当前用户，拒绝外部传入 userId。
 */
@RestController
@RequestMapping("/api/interest-tag")
@RequiredArgsConstructor
@Slf4j
public class InterestTagController {

    private final InterestTagGenerateService interestTagGenerateService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    private final EndpointMetrics endpointMetrics;
    private final ExecutorService tagExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 手动触发兴趣标签生成。
     * 异步执行（约 2 分钟），立即返回 accepted，不阻塞 Tomcat 线程。
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateTags() {
        return endpointMetrics.recordResult("web", "interest_tag.generate", () -> {
            String userId = userContext.getUserId();
            if (userId == null || userId.isEmpty()) {
                return Result.error(401, "未认证用户无法生成标签");
            }
            if (!rateLimiter.tryAcquire("interest:tag:" + userId, 1, 3600_000L)) {
                return Result.error(429, "操作过于频繁，请一小时后再试");
            }
            CompletableFuture.runAsync(() -> {
                try {
                    interestTagGenerateService.generateTags(userId);
                } catch (Exception e) {
                    log.warn("异步标签生成异常（已降级）: userId={}", userId, e);
                }
            }, tagExecutor);
            return Result.success(Map.of("status", "accepted", "message", "标签生成任务已提交，请稍后查看"));
        });
    }

    @PreDestroy
    void shutdown() {
        tagExecutor.shutdown();
    }
}

