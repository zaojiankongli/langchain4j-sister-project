package com.zjkl.emotion.service;

import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.mapper.ConverMessageMapper;
import com.zjkl.emotion.assistant.EmotionReasonAgent;
import com.zjkl.emotion.mapper.UserEmotionMapper;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.UserEmotionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时情绪记录服务
 * 负责记录用户定时情绪快照到 user_emotions 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionRecordService {

    private static final int CHAT_HISTORY_HOURS = 4;
    private static final int MAX_CHAT_LENGTH = 2000;

    private final EmotionService emotionService;
    private final EmotionReasonAgent emotionReasonAgent;
    private final ConverMessageMapper converMessageMapper;
    private final UserEmotionMapper userEmotionMapper;

    /**
     * 异步记录用户情绪快照
     * 1. 获取当前 PAD 情绪
     * 2. 获取最近聊天记录
     * 3. 异步调用 LLM 生成原因
     * 4. 写入 user_emotions 表
     */
    @Async
    public void recordEmotionAsync(String userId) {
        log.debug("开始异步记录用户情绪: userId={}", userId);

        try {
            // 1. 获取当前情绪
            EmotionalState emotion = emotionService.getUserEmotion(userId);
            String moodLabel = emotionService.getUserMoodLabel(userId);
            String moodDesc = emotionService.getUserMoodDescription(userId);

            // 2. 获取最近 4 小时聊天记录
            String chatHistory = queryChatHistory(userId);

            // 3. 调用 LLM 生成原因
            String reason = callLlmForReason(userId, emotion, moodLabel, moodDesc, chatHistory);

            // 4. 拼装 mood_description（情绪描述 + 原因）
            String fullMoodDesc = moodDesc + " —— " + reason;

            // 5. 写入数据库
            UserEmotionRecord record = UserEmotionRecord.builder()
                    .userId(userId)
                    .pleasure(BigDecimal.valueOf(emotion.getPleasure()))
                    .arousal(BigDecimal.valueOf(emotion.getArousal()))
                    .dominance(BigDecimal.valueOf(emotion.getDominance()))
                    .moodDescription(fullMoodDesc)
                    .aiType(1)
                    .build();

            userEmotionMapper.insert(record);

            log.info("用户情绪记录成功: userId={}, mood={}, reason={}",
                    userId, moodLabel, reason);
        } catch (Exception e) {
            log.error("用户情绪记录失败: userId={}", userId, e);
        }
    }

    /**
     * 查询最近 4 小时聊天记录
     */
    private String queryChatHistory(String userId) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(CHAT_HISTORY_HOURS);
        List<ConverMessage> messages = converMessageMapper.selectByUserIdAndTimeRange(
                userId, startTime, LocalDateTime.now());

        if (messages.isEmpty()) {
            return "暂无聊天记录";
        }

        String history = messages.stream()
                .map(msg -> {
                    String role = "user".equals(msg.getRole()) ? "哥哥/姐姐" : "妹妹(早空)";
                    String text = msg.getContents().stream()
                            .filter(c -> "text".equals(c.getType()))
                            .map(c -> c.getText())
                            .collect(Collectors.joining(" "));
                    return role + ": " + text;
                })
                .collect(Collectors.joining("\n"));

        return history.length() > MAX_CHAT_LENGTH
                ? history.substring(0, MAX_CHAT_LENGTH) + "..."
                : history;
    }

    /**
     * 调用 LLM 生成情绪原因
     */
    private String callLlmForReason(String userId, EmotionalState emotion,
                                     String moodLabel, String moodDesc, String chatHistory) {
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            return emotionReasonAgent.generateReason(
                    time,
                    moodLabel,
                    String.format("%.3f", emotion.getPleasure()),
                    String.format("%.3f", emotion.getArousal()),
                    String.format("%.3f", emotion.getDominance()),
                    chatHistory
            );
        } catch (Exception e) {
            log.warn("LLM 生成情绪原因失败: userId={}", userId, e);
            return "静静地陪在哥哥/姐姐身边~";
        }
    }
}
