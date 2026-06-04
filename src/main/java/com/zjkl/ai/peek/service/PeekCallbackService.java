package com.zjkl.ai.peek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.VoiceSynthesisService;
import com.zjkl.ai.peek.agent.PeekContentAgent;
import com.zjkl.ai.peek.tool.PeekStateTool;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Peek 截图回调处理服务
 *
 * 核心链路（异步执行，不阻塞 REST 线程）：
 * VLM 截图理解 → Agent 生成关怀 → TTS 合成 → 消息持久化 → WebSocket 推送 → 记录 peek 时间
 *
 * 链路耗时约 5-13 秒（VLM 1-3s + Agent 1-2s + TTS 1-3s），全程异步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeekCallbackService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DEFAULT_PEEK_MESSAGE = "刚刚看了一下，你正在专注呢，我就不打扰啦～";

    private final ImageDescriptionService imageDescriptionService;
    private final PeekContentAgent peekContentAgent;
    private final EmotionService emotionService;
    private final VoiceSynthesisService voiceSynthesisService;
    private final PromptTemplateService promptTemplateService;
    private final ConverMessageService converMessageService;
    private final ChatPushService chatPushService;
    private final PeekStateTool peekStateTool;
    private final TimeContextTool timeContextTool;
    private final UserStateTool userStateTool;

    /**
     * 异步处理 peek 截图回调
     *
     * @param userId   用户 ID
     * @param imageUrl 截图的 OSS URL
     * @param peekId   peek 任务 ID（用于日志追踪）
     */
    @Async
    public void handlePeekCallback(String userId, String imageUrl, String peekId) {
        log.info("开始处理 peek 回调：userId={}, peekId={}", userId, peekId);

        try {
            // 0. 离线重检 — 避免昂贵的 VLM/Agent/TTS 白做
            if (!chatPushService.isUserConnected(userId)) {
                log.info("用户已断开，跳过 peek 处理：userId={}, peekId={}", userId, peekId);
                return;
            }

            // 1. VLM 理解截图
            String screenshotDesc = imageDescriptionService.describeForPeek(imageUrl);
            log.info("peek VLM 描述完成：userId={}", userId);

            // 2. 获取上下文
            TimeContextTool.TimeContext timeContext = timeContextTool.getCurrentContext();
            EmotionalState currentEmotion = emotionService.getUserEmotion(userId);
            String moodDesc = emotionService.getMoodDescription(currentEmotion);
            int activeMinutes = peekStateTool.getContinuousActiveMinutes(userId);

            // 渲染 characterCore（角色身份 + 动态情绪参数）
            String moodLabel = emotionService.getUserMoodLabel(userId);
            String characterCore = promptTemplateService.render("character/core", Map.of(
                "pleasure", String.format("%.3f", currentEmotion.getPleasure()),
                "arousal", String.format("%.3f", currentEmotion.getArousal()),
                "dominance", String.format("%.3f", currentEmotion.getDominance()),
                "moodLabel", moodLabel != null ? moodLabel : ""
            ));

            // 3. Agent 生成关怀文本（JSON 格式，含 voiceParams）
            String rawOutput = peekContentAgent.generateMessage(
                    characterCore,
                    screenshotDesc,
                    timeContext.timeOfDay(),
                    timeContext.specialMoment(),
                    moodDesc,
                    activeMinutes,
                    userId
            );

            if (rawOutput == null || rawOutput.isBlank()) {
                log.warn("peek Agent 生成内容为空：userId={}, peekId={}", userId, peekId);
                return;
            }

            String content;
            VoiceSynthesisParam voiceParams = null;
            boolean isFallback = false;
            String trimmed = rawOutput.trim();
            if (trimmed.startsWith("{")) {
                try {
                    JsonNode node = objectMapper.readTree(trimmed);
                    content = node.path("message").asText(null);
                    JsonNode vpNode = node.path("voiceParams");
                    if (!vpNode.isNull() && !vpNode.isMissingNode()) {
                        voiceParams = new VoiceSynthesisParam();
                        if (vpNode.has("volume")) voiceParams.setVolume(vpNode.path("volume").asInt(50));
                        if (vpNode.has("speechRate")) voiceParams.setSpeechRate((float) vpNode.path("speechRate").asDouble(1.0));
                        if (vpNode.has("pitchRate")) voiceParams.setPitchRate((float) vpNode.path("pitchRate").asDouble(1.0));
                        if (vpNode.has("instruction")) voiceParams.setInstruction(vpNode.path("instruction").asText(null));
                        if (vpNode.has("voice")) voiceParams.setVoice(vpNode.path("voice").asText(null));
                    }
                } catch (Exception e) {
                    log.warn("peek JSON 解析失败，降级为纯文本: {}", rawOutput, e);
                    content = trimmed.replaceAll("^[\"']+|[\"']+$", "");
                    isFallback = true;
                }
            } else {
                content = trimmed.replaceAll("^[\"']+|[\"']+$", "");
                isFallback = true;
            }

            content = content != null ? content.trim() : null;

            if (content == null || content.isBlank()) {
                log.warn("peek Agent 提取消息为空，使用通用兜底文案：userId={}, peekId={}", userId, peekId);
                content = DEFAULT_PEEK_MESSAGE;
                isFallback = true;
            }

            // 仅对降级文本做安全兜底：如果仍像 JSON 片段（含 { 或 "），用通用消息替代
            if (isFallback && content != null && (content.contains("\"") || content.contains("{"))) {
                log.warn("peek 降级文本仍含 JSON 结构，垫通用消息：content={}", content);
                content = DEFAULT_PEEK_MESSAGE;
            }

            log.info("peek Agent 生成内容：userId={}, content=***, useLLMParams={}", userId, voiceParams != null);

            // 4. TTS 合成
            ByteBuffer audioBuffer;
            if (voiceParams != null) {
                audioBuffer = voiceSynthesisService.synthesize(content, voiceParams);
            } else {
                audioBuffer = voiceSynthesisService.synthesize(content, emotionService.getUserEmotion(userId));
            }

            byte[] audioData;
            if (audioBuffer == null) {
                log.warn("TTS synthesis returned null for user {}, skipping audio", userId);
                audioData = new byte[0];
            } else {
                audioData = new byte[audioBuffer.remaining()];
                if (audioData.length > 0) {
                    audioBuffer.get(audioData);
                }
                log.info("peek TTS 合成完成：userId={}, audioSize={} bytes", userId, audioData.length);
            }

            // 5. 持久化消息
            try {
                converMessageService.saveMessage(userId, "assistant", List.of(MessageContent.text(content)));
            } catch (Exception e) {
                log.warn("peek 消息持久化失败（不影响发送）：userId={}", userId, e);
            }

            // 6. 推送文本 + 语音
            chatPushService.pushText(userId, content, true);
            // 7. 记录 peek 时间 + 设置 wakeup 互斥（文本已成功送达即可视为处理完成）
            peekStateTool.recordPeek(userId);
            userStateTool.recordWakeUp(userId);
            if (audioData.length > 0) {
                try {
                    chatPushService.pushAudio(userId, audioData);
                } catch (Exception audioEx) {
                    log.warn("peek 语音推送失败，保留已发送文本：userId={}, peekId={}, error={}",
                            userId, peekId, audioEx.getMessage());
                }
            }

            log.info("peek 处理完成（文本+语音已推送）：userId={}, peekId={}, textLength={}",
                    userId, peekId, content.length());

        } catch (Exception e) {
            log.error("peek 处理失败：userId={}, peekId={}", userId, peekId, e);
            chatPushService.pushError(userId, "peek 处理失败了，下次再试试吧~");
        }
    }
}
