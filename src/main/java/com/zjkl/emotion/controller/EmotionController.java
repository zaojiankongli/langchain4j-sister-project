package com.zjkl.emotion.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.vo.EmotionHistoryVO;
import com.zjkl.emotion.model.vo.EvolutionEventVO;
import com.zjkl.emotion.service.EmotionService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@Validated
public class EmotionController {

    private static final int MAX_LIMIT = 100;

    private final EmotionService emotionService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getEmotion(@PathVariable @Size(max = 64) String userId) {
        return endpointMetrics.recordResult("web", "emotion.current", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            EmotionalState emotion = emotionService.getUserEmotion(userId);
            return Result.success(Map.of(
                    "userId", userId,
                    "pleasure", emotion.getPleasure(),
                    "arousal", emotion.getArousal(),
                    "dominance", emotion.getDominance()
            ));
        });
    }

    @GetMapping("/{userId}/mood")
    public Result<Map<String, String>> getMood(@PathVariable @Size(max = 64) String userId) {
        return endpointMetrics.recordResult("web", "emotion.mood", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            return Result.success(Map.of(
                    "userId", userId,
                    "description", emotionService.getUserMoodDescription(userId),
                    "label", emotionService.getUserMoodLabel(userId)
            ));
        });
    }

    @GetMapping("/{userId}/evolution")
    public Result<List<EvolutionEventVO>> getEvolution(
            @PathVariable @Size(max = 64) String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return endpointMetrics.recordResult("web", "emotion.evolution", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
            List<EvolutionEventVO> voList = emotionService.getEvolutionEvents(userId, safeLimit);
            return Result.success(voList);
        });
    }

    @GetMapping("/{userId}/history")
    public Result<List<EmotionHistoryVO>> getHistory(
            @PathVariable @Size(max = 64) String userId,
            @RequestParam(defaultValue = "200") int limit) {
        return endpointMetrics.recordResult("web", "emotion.history", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
            List<EmotionHistoryVO> voList = emotionService.getEmotionHistory(userId, safeLimit);
            return Result.success(voList);
        });
    }

    @PostMapping("/{userId}/reset")
    public Result<Map<String, Object>> resetEmotion(@PathVariable @Size(max = 64) String userId) {
        return endpointMetrics.recordResult("web", "emotion.reset", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            emotionService.resetUserEmotion(userId);
            EmotionalState emotion = emotionService.getUserEmotion(userId);
            return Result.success(Map.of(
                    "userId", userId,
                    "message", "情绪已重置",
                    "pleasure", emotion.getPleasure(),
                    "arousal", emotion.getArousal(),
                    "dominance", emotion.getDominance()
            ));
        });
    }
}
