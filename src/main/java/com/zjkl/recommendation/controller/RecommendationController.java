package com.zjkl.recommendation.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.service.RecommendationService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 资源推荐 REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/recom")
@RequiredArgsConstructor
public class RecommendationController {

    private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final RecommendationService recommendationService;
    private final UserContext userContext;

    @PreDestroy
    public void shutdown() {
        requestExecutor.shutdown();
        try {
            if (!requestExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取当前用户的今日推荐
     * GET /ai/recom
     */
    @GetMapping
    public Result<List<UserRecommendation>> getRecommendations() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        List<UserRecommendation> recommendations = recommendationService.getTodayRecommendations(userId);
        return Result.success(recommendations);
    }

    /**
     * 获取当前用户的今日推荐（通过路径参数，与 UserContext 校验一致性）
     * GET /ai/recom/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public Result<List<UserRecommendation>> getUserRecommendations(@PathVariable String userId) {
        String currentUserId = userContext.getUserId();
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }
        if (!currentUserId.equals(userId)) {
            return Result.error(403, "无权访问其他用户的推荐");
        }
        List<UserRecommendation> recommendations = recommendationService.getTodayRecommendations(userId);
        return Result.success(recommendations);
    }

    /**
     * 标记推荐为已点击（仅限当前用户自己的推荐）
     * POST /ai/recom/click?id=xxx
     */
    @PostMapping("/click")
    public Result<Void> markAsClicked(@RequestParam Long id) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        // 所有权检查：确保该推荐属于当前用户
        UserRecommendation rec = recommendationService.findById(id);
        if (rec == null) {
            return Result.error(404, "推荐不存在");
        }
        if (!userId.equals(rec.getUserId())) {
            return Result.error(403, "无权操作其他用户的推荐");
        }
        recommendationService.markAsClicked(id);
        return Result.success(null);
    }

    /**
     * 手动触发推荐生成（异步，不阻塞 HTTP 线程）
     * POST /ai/recom/generate
     */
    @PostMapping("/generate")
    public DeferredResult<Result<String>> generateRecommendations() {
        String userId = userContext.getUserId();
        if (userId == null) {
            DeferredResult<Result<String>> errorResult = new DeferredResult<>();
            errorResult.setResult(Result.unauthorized("请先登录"));
            return errorResult;
        }

        DeferredResult<Result<String>> deferredResult = new DeferredResult<>(300_000L); // 5min timeout

        CompletableFuture
                .supplyAsync(() -> {
                    List<UserRecommendation> recommendations = recommendationService.generateRecommendations(userId);
                    return "生成了 " + recommendations.size() + " 条推荐";
                }, requestExecutor)
                .whenCompleteAsync((result, ex) -> {
                    if (ex != null) {
                        log.error("异步推荐生成失败: userId={}", userId, ex);
                        deferredResult.setResult(Result.error(500, "推荐生成失败，请稍后重试"));
                    } else {
                        deferredResult.setResult(Result.success(result));
                    }
                }, requestExecutor);

        deferredResult.onTimeout(() ->
                deferredResult.setResult(Result.error(408, "推荐生成超时"))
        );

        return deferredResult;
    }
}
