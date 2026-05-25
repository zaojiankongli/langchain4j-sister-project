package com.zjkl.recommendation.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 资源推荐 REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/recom")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserContext userContext;

    @Value("${recommendation.demo-user:demo_user}")
    private String demoUser;

    /**
     * 获取当前用户的今日推荐
     * GET /ai/recom
     */
    @GetMapping
    public Result<List<UserRecommendation>> getRecommendations() {
        String userId = Objects.requireNonNull(userContext.getUserId(), "userId must not be null");
        List<UserRecommendation> recommendations = recommendationService.getTodayRecommendations(userId);
        return Result.success(recommendations);
    }

    /**
     * 获取指定用户的今日推荐
     * GET /ai/recom?userId=xxx
     */
    @GetMapping("/user/{userId}")
    public Result<List<UserRecommendation>> getUserRecommendations(@PathVariable String userId) {
        List<UserRecommendation> recommendations = recommendationService.getTodayRecommendations(userId);
        return Result.success(recommendations);
    }

    /**
     * 标记推荐为已点击
     * POST /ai/recom/click?id=xxx
     */
    @PostMapping("/click")
    public Result<Void> markAsClicked(@RequestParam Long id) {
        recommendationService.markAsClicked(id);
        return Result.success(null);
    }

    /**
     * 手动触发推荐生成（仅用于测试）
     * POST /ai/recom/generate?userId=xxx
     */
    @PostMapping("/generate")
    public Result<String> generateRecommendations(@RequestParam(required = false) String userId) {
        if (userId == null) {
            userId = demoUser;
        }

        List<UserRecommendation> recommendations = recommendationService.generateRecommendations(userId);
        return Result.success("生成了 " + recommendations.size() + " 条推荐");
    }
}
