package com.zjkl.emotion.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.vo.EmotionHistoryVO;
import com.zjkl.emotion.model.vo.EvolutionEventVO;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
public class EmotionController {

    private static final int MAX_LIMIT = 100;

    private final EmotionService emotionService;
    private final UserContext userContext;

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getEmotion(@PathVariable String userId) {
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
    }

    @GetMapping("/{userId}/mood")
    public Result<Map<String, String>> getMood(@PathVariable String userId) {
        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        return Result.success(Map.of(
                "userId", userId,
                "description", emotionService.getUserMoodDescription(userId),
                "label", emotionService.getUserMoodLabel(userId)
        ));
    }

    @GetMapping("/{userId}/evolution")
    public Result<List<EvolutionEventVO>> getEvolution(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        limit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<EvolutionEventVO> voList = emotionService.getEvolutionEvents(userId, limit);
        return Result.success(voList);
    }

    @GetMapping("/{userId}/history")
    public Result<List<EmotionHistoryVO>> getHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "200") int limit) {
        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        limit = Math.max(1, Math.min(limit, 500));
        List<EmotionHistoryVO> voList = emotionService.getEmotionHistory(userId, limit);
        return Result.success(voList);
    }

    @PostMapping("/{userId}/reset")
    public Result<Map<String, Object>> resetEmotion(@PathVariable String userId) {
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
    }
}
