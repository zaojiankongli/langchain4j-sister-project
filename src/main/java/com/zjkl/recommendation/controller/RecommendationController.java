package com.zjkl.recommendation.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.service.RecommendationService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 资源推荐 REST API 控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/recom")
@RequiredArgsConstructor
public class RecommendationController {

    private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final RecommendationService recommendationService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

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

    @GetMapping
    public Result<List<UserRecommendation>> getRecommendations() {
        return endpointMetrics.recordResult("web", "recommendation.list", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            List<UserRecommendation> recommendations = recommendationService.getTodayRecommendations(userId);
            return Result.success(recommendations);
        });
    }

    @PostMapping("/click")
    public Result<Void> markAsClicked(@RequestParam Long id) {
        return endpointMetrics.recordResult("web", "recommendation.click", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            UserRecommendation rec = recommendationService.findById(id);
            if (rec == null) {
                return Result.error(404, "推荐不存在");
            }
            if (!userId.equals(rec.getUserId())) {
                return Result.error(403, "无权操作其他用户的推荐");
            }
            recommendationService.markAsClicked(id);
            return Result.success(null);
        });
    }

    @PostMapping("/generate")
    public DeferredResult<Result<String>> generateRecommendations() {
        return endpointMetrics.recordDeferredResult("web", "recommendation.generate", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                DeferredResult<Result<String>> errorResult = new DeferredResult<>();
                errorResult.setResult(Result.unauthorized("请先登录"));
                return errorResult;
            }

            DeferredResult<Result<String>> deferredResult = new DeferredResult<>(300_000L);

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
        });
    }
}
