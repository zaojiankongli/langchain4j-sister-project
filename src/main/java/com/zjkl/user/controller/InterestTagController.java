package com.zjkl.user.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.user.service.InterestTagGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户兴趣标签控制器
 * 认证方式：从 UserContext 获取当前用户，拒绝外部传入 userId
 */
@RestController
@RequestMapping("/api/interest-tag")
@RequiredArgsConstructor
public class InterestTagController {

    private final InterestTagGenerateService interestTagGenerateService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    private final ExecutorService tagExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 手动触发兴趣标签生成
     * 异步执行（约 2 分钟），立即返回 202 Accepted，不阻塞 Tomcat 线程
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateTags() {
        String userId = userContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error(401, "未认证用户无法生成标签");
        }
        // 限流：每用户每小时最多 1 次
        if (!rateLimiter.tryAcquire("interest:tag:" + userId, 1, 3600_000L)) {
            return Result.error(429, "操作过于频繁，请一小时后再试");
        }
        // 异步执行，不阻塞 Tomcat 线程
        CompletableFuture.runAsync(() -> {
            try {
                interestTagGenerateService.generateTags(userId);
            } catch (Exception e) {
                // 已在 service 层记录日志
            }
        }, tagExecutor);
        return Result.error(202, "标签生成任务已提交，请稍后查看");
    }
}
