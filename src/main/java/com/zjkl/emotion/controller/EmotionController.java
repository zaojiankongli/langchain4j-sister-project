package com.zjkl.emotion.controller;

import com.zjkl.emotion.mapper.EmotionAnchorMapper;
import com.zjkl.emotion.mapper.UserEmotionMapper;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.UserEmotionRecord;
import com.zjkl.emotion.model.vo.EmotionHistoryVO;
import com.zjkl.emotion.model.vo.EvolutionEventVO;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.user.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
public class EmotionController {

    private final EmotionService emotionService;
    private final EmotionAnchorMapper emotionAnchorMapper;
    private final UserEmotionMapper userEmotionMapper;

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getEmotion(@PathVariable String userId) {
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
        List<Map<String, Object>> events = emotionAnchorMapper.selectEvolutionByUserId(userId, limit);
        List<EvolutionEventVO> voList = events.stream().map(row -> {
            EvolutionEventVO vo = new EvolutionEventVO();
            vo.setTrigger((String) row.get("trigger_reason"));
            String highlightTraits = (String) row.get("highlight_traits");
            vo.setResult(highlightTraits != null && !highlightTraits.isEmpty()
                    ? highlightTraits
                    : (String) row.get("ai_reflection"));
            Object startTimeObj = row.get("start_time");
            LocalDateTime startTime = null;
            if (startTimeObj instanceof LocalDateTime) {
                startTime = (LocalDateTime) startTimeObj;
            } else if (startTimeObj instanceof Date) {
                startTime = ((Date) startTimeObj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            vo.setTime(formatRelativeTime(startTime));
            return vo;
        }).collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/{userId}/history")
    public Result<List<EmotionHistoryVO>> getHistory(@PathVariable String userId) {
        List<UserEmotionRecord> records = userEmotionMapper.selectByUserIdNoLimit(userId);
        List<EmotionHistoryVO> voList = records.stream().map(r -> {
            EmotionHistoryVO vo = new EmotionHistoryVO();
            vo.setId(r.getId());
            vo.setPleasure(r.getPleasure());
            vo.setArousal(r.getArousal());
            vo.setDominance(r.getDominance());
            vo.setMoodDescription(r.getMoodDescription());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
        return Result.success(voList);
    }

    @PostMapping("/{userId}/reset")
    public Result<Map<String, Object>> resetEmotion(@PathVariable String userId) {
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

    private String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = duration.toHours();
        if (hours < 24) return hours + "小时前";
        long days = duration.toDays();
        if (days < 30) return days + "天前";
        return dateTime.toLocalDate().toString().replace("-", ".");
    }
}
