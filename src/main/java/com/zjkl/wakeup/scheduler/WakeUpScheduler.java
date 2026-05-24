package com.zjkl.wakeup.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.VoiceSynthesisService;
import com.zjkl.wakeup.agent.*;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主动唤醒调度 — Agentic 架构：
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpScheduler {

    private final UserActivityTracker userActivityTracker;
    private final UserStateTool userStateTool;
    private final TimeContextTool timeContextTool;
    private final EmotionService emotionService;
    private final VoiceSynthesisService voiceSynthesisService;
    private final ChatPushService chatPushService;
    private final ConverMessageService converMessageService;
    private final StringRedisTemplate redisTemplate;

    private final WakeUpGenerator1Agent generator1Agent;
    private final WakeUpGenerator2Agent generator2Agent;
    private final WakeUpGenerator3Agent generator3Agent;
    private final WakeUpScorer1Agent scorer1Agent;
    private final WakeUpScorer2Agent scorer2Agent;
    private final WakeUpScorer3Agent scorer3Agent;
    private final WakeUpArbiterAgent arbiterAgent;
    private final WakeUpTracker wakeUpTracker;

    @Value("${wake-up.enabled:true}")
    private boolean wakeUpEnabled;

    @Value("${wake-up.silent-threshold-minutes:90}")
    private int silentThresholdMinutes;

    @Value("${wake-up.cooldown-minutes:30}")
    private int cooldownMinutes;

    private final Executor wakeupExecutor = Thread::startVirtualThread;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        log.info("唤醒执行器已初始化（虚拟线程）");
    }

    private Executor getExecutor() {
        return wakeupExecutor;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void checkUsersForWakeUp() {
        if (!wakeUpEnabled) {
            log.debug("主动唤醒功能已禁用");
            return;
        }

        Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(7);
        if (activeUsers.isEmpty()) {
            log.debug("无活跃用户");
            return;
        }

        TimeContextTool.TimeContext timeContext = timeContextTool.getCurrentContext();
        log.info("唤醒心跳：时间={}, 时段={}, 特殊时间={}",
                timeContext.currentTime(), timeContext.timeOfDay(), timeContext.specialMoment());

            AtomicInteger passFilter = new AtomicInteger(0);
            AtomicInteger passProb = new AtomicInteger(0);
            AtomicInteger sentCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = activeUsers.stream()
                    .map(userId -> CompletableFuture.runAsync(() -> {
                        try {
                            int result = processUserWakeUp(userId, timeContext);
                            if (result >= 1) passFilter.incrementAndGet();
                            if (result >= 2) passProb.incrementAndGet();
                            if (result >= 3) sentCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("处理用户唤醒失败：userId={}", userId, e);
                        }
                    }, getExecutor()))
                    .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("唤醒检查完成：总用户={}, 通过过滤={}, 通过概率={}, 实际发送={}",
                activeUsers.size(), passFilter.get(), passProb.get(), sentCount.get());
    }

    private static final String PROCESSING_KEY_PREFIX = "wakeup:processing:";
    private static final long PROCESSING_KEY_TTL_SECONDS = 600;

    /**
     * 核心流程：3 并行生成 → 过滤 → 并行评分 → 仲裁 → A/B → 发送
     */
    private int processUserWakeUp(String userId, TimeContextTool.TimeContext timeContext) {
        // === 0. Redis 用户级去重 ===
        String processingKey = PROCESSING_KEY_PREFIX + userId;
        Boolean alreadyProcessing = redisTemplate.opsForValue().setIfAbsent(processingKey, "1",
                java.time.Duration.ofSeconds(PROCESSING_KEY_TTL_SECONDS));
        if (Boolean.FALSE.equals(alreadyProcessing)) {
            log.debug("用户正在被其他线程处理中，跳过：userId={}", userId);
            return 0;
        }

        try {
            // === 1. 过滤条件 ===
            boolean isDnd = userStateTool.isDoNotDisturb(userId);
            if (isDnd) {
                log.debug("用户处于免打扰状态，跳过唤醒：userId={}", userId);
                return 0;
            }
            Integer minutesSinceLastWakeup = userStateTool.getMinutesSinceLastWakeup(userId);
            if (minutesSinceLastWakeup < cooldownMinutes) {
                log.debug("冷却期内，跳过唤醒：userId={}, minutesSinceLastWakeup={}min, cooldown={}min",
                        userId, minutesSinceLastWakeup, cooldownMinutes);
                return 0;
            }
            Double silentHours = userStateTool.getSilentHours(userId);

            double probability = userStateTool.calculateWakeProbability(userId, silentHours, timeContext);
            log.info("概率计算：userId={}, probability={}, silentHours={}h",
                    userId, String.format("%.3f", probability), String.format("%.1f", silentHours));

            // 概率过滤：不满足概率随机则跳过
            if (Math.random() > probability) {
                log.debug("概率过滤未通过：userId={}, probability={}", userId, String.format("%.3f", probability));
                return 1;
            }

        // === 2. 构建上下文 ===
        UserStateTool.UserStateSnapshot state = userStateTool.buildStateSnapshot(
                userId, timeContext, isDnd, silentHours, minutesSinceLastWakeup);
        String anchorHint = buildAnchorHint(state);

        // === 3. 并行调用 3 个 Generator Agent ===
        log.info("开始并行生成问候：userId={}", userId);
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() ->
                generator1Agent.generate(timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), state.silentHours(), anchorHint, userId),
                getExecutor()).exceptionally(e -> { log.warn("Generator1 失败: {}", e.getMessage()); return null; });

        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() ->
                generator2Agent.generate(timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), state.silentHours(), anchorHint, userId),
                getExecutor()).exceptionally(e -> { log.warn("Generator2 失败: {}", e.getMessage()); return null; });

        CompletableFuture<String> future3 = CompletableFuture.supplyAsync(() ->
                generator3Agent.generate(timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), state.silentHours(), anchorHint, userId),
                getExecutor()).exceptionally(e -> { log.warn("Generator3 失败: {}", e.getMessage()); return null; });

        String raw1 = future1.join();
        String raw2 = future2.join();
        String raw3 = future3.join();

        log.info("生成结果：userId={}, 候选1={}, 候选2={}, 候选3={}", userId, raw1, raw2, raw3);

        // === 4. 解析 JSON 提取 message，过滤无效候选 ===
        GeneratorOutput out1 = parseGeneratorOutput(raw1);
        GeneratorOutput out2 = parseGeneratorOutput(raw2);
        GeneratorOutput out3 = parseGeneratorOutput(raw3);

        List<GeneratorOutput> candidates = new ArrayList<>();
        candidates.add(isValidCandidate(out1) ? out1 : null);
        candidates.add(isValidCandidate(out2) ? out2 : null);
        candidates.add(isValidCandidate(out3) ? out3 : null);

        long validCount = candidates.stream().filter(c -> c != null).count();

        if (validCount == 0) {
            String fallbackMsg = timeContext.greeting() + "～今天过得怎么样呀";
            log.info("无有效候选，使用 fallback：userId={}, msg={}", userId, fallbackMsg);
            saveWakeUpMessageAsync(userId, fallbackMsg);
            sendWakeUpWithVoice(userId, fallbackMsg, null);
            return 3;
        }

        if (validCount == 1) {
            GeneratorOutput chosen = candidates.stream().filter(c -> c != null).findFirst().get();
            String msg = chosen.message;
            log.info("仅有一条有效候选，直接使用：userId={}, msg={}", userId, msg);
            WakeUpTracker.SwapResult swapResult = wakeUpTracker.maybeSwap(
                    candidates.stream().map(c -> c != null ? c.message : null).toList(),
                    new int[]{0, 0, 0}, candidates.indexOf(chosen));
            msg = swapResult.getMessage();
            saveWakeUpMessageAsync(userId, msg);
            sendWakeUpWithVoice(userId, msg, chosen.voiceParams);
            wakeUpTracker.recordSent(userId,
                    candidates.stream().map(c -> c != null ? c.message : null).toList(),
                    new int[]{0, 0, 0},
                    candidates.indexOf(chosen), swapResult.getOriginalBestIndex(), msg);
            return 3;
        }

        // === 5. 并行评分（>= 2 条有效）===
        log.info("开始并行评分：userId={}", userId);
        String candidateMsg1 = candidates.get(0) != null ? candidates.get(0).message : null;
        String candidateMsg2 = candidates.get(1) != null ? candidates.get(1).message : null;
        String candidateMsg3 = candidates.get(2) != null ? candidates.get(2).message : null;

        CompletableFuture<String> scoreFuture1 = CompletableFuture.supplyAsync(() ->
                scorer1Agent.score(candidateMsg1, timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), userId), getExecutor())
                .exceptionally(e -> { log.warn("Scorer1 失败: {}", e.getMessage()); return "{\"score\":5,\"reason\":\"评分失败\"}"; });

        CompletableFuture<String> scoreFuture2 = CompletableFuture.supplyAsync(() ->
                scorer2Agent.score(candidateMsg2, timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), state.silentHours(), userId), getExecutor())
                .exceptionally(e -> { log.warn("Scorer2 失败: {}", e.getMessage()); return "{\"score\":5,\"reason\":\"评分失败\"}"; });

        CompletableFuture<String> scoreFuture3 = CompletableFuture.supplyAsync(() ->
                scorer3Agent.score(candidateMsg3, timeContext.timeOfDay(), timeContext.specialMoment(),
                        state.moodDescription(), state.moodScore(), anchorHint, state.silentHours(), userId), getExecutor())
                .exceptionally(e -> { log.warn("Scorer3 失败: {}", e.getMessage()); return "{\"score\":5,\"reason\":\"评分失败\"}"; });

        String scoreJson1 = scoreFuture1.join();
        String scoreJson2 = scoreFuture2.join();
        String scoreJson3 = scoreFuture3.join();

        WakeUpScoreResult sr1 = parseScoreResult(scoreJson1);
        WakeUpScoreResult sr2 = parseScoreResult(scoreJson2);
        WakeUpScoreResult sr3 = parseScoreResult(scoreJson3);

        log.info("评分结果：userId={}, 评分1={}({}), 评分2={}({}), 评分3={}({})",
                userId, sr1.getScore(), sr1.getReason(), sr2.getScore(), sr2.getReason(),
                sr3.getScore(), sr3.getReason());

        // === 6. 仲裁 ===
        String arbiterResult = arbiterAgent.decide(
                timeContext.timeOfDay(), timeContext.specialMoment(),
                state.moodDescription(), state.moodScore(), state.silentHours(), anchorHint,
                candidateMsg1, sr1.getScore(), sr1.getReason(),
                candidateMsg2, sr2.getScore(), sr2.getReason(),
                candidateMsg3, sr3.getScore(), sr3.getReason());

        log.info("仲裁结果：userId={}, result={}", userId, arbiterResult);

        List<String> candidateMessages = List.of(candidateMsg1, candidateMsg2, candidateMsg3);
        ArbiterDecision decision = parseArbiterResult(arbiterResult, candidateMessages, timeContext);
        int bestIndex = decision.bestIndex;

        // === 7. A/B 测试 ===
        WakeUpTracker.SwapResult swapResult = wakeUpTracker.maybeSwap(candidateMessages,
                new int[]{sr1.getScore(), sr2.getScore(), sr3.getScore()}, bestIndex);

        // === 8. 发送 ===
        String finalMessage = swapResult.getMessage();
        VoiceSynthesisParam finalVoiceParams = null;
        int finalBestIndex = bestIndex;
        if (bestIndex >= 0 && bestIndex < candidates.size() && candidates.get(bestIndex) != null) {
            finalVoiceParams = candidates.get(bestIndex).voiceParams;
        } else {
            for (GeneratorOutput c : candidates) {
                if (c != null) {
                    finalBestIndex = candidates.indexOf(c);
                    finalVoiceParams = c.voiceParams;
                    break;
                }
            }
        }

        saveWakeUpMessageAsync(userId, finalMessage);
        sendWakeUpWithVoice(userId, finalMessage, finalVoiceParams);

        wakeUpTracker.recordSent(userId, candidateMessages,
                new int[]{sr1.getScore(), sr2.getScore(), sr3.getScore()},
                finalBestIndex, swapResult.getOriginalBestIndex(), finalMessage);

        return 3;
        } finally {
            redisTemplate.delete(processingKey);
        }
    }

    /**
     * 手动唤醒
     */
    public void triggerWakeUpCheck(String userId) {
        log.info("手动触发唤醒检查：userId={}", userId);
        var timeContext = timeContextTool.getCurrentContext();
        processUserWakeUp(userId, timeContext);
    }

    // ========== 辅助方法 ==========

    private String buildAnchorHint(UserStateTool.UserStateSnapshot state) {
        StringBuilder sb = new StringBuilder();
        if (state.activeAnchorContext() != null) {
            sb.append(state.activeAnchorContext());
        }
        if (!"无历史锚点事件".equals(state.recentAnchorSummary())
                && !"无已结束的锚点事件".equals(state.recentAnchorSummary())) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append(state.recentAnchorSummary());
        }
        return sb.isEmpty() ? "无特殊事件" : sb.toString();
    }

    private GeneratorOutput parseGeneratorOutput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            String msg = trimmed.replaceAll("^[\"']+|[\"']+$", "").trim();
            if (msg.length() < 4) return null;
            msg = truncateMessage(msg);
            return new GeneratorOutput(msg, null);
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) return null;
            message = message.trim().replaceAll("^[\"']+|[\"']+$", "");
            message = truncateMessage(message);
            VoiceSynthesisParam vp = parseVoiceParams(node.path("voiceParams"));
            return new GeneratorOutput(message, vp);
        } catch (Exception e) {
            log.warn("解析 Generator JSON 失败: {}", raw, e);
            return null;
        }
    }

    private String truncateMessage(String msg) {
        if (msg != null && msg.length() > 20) {
            return msg.substring(0, 20);
        }
        return msg;
    }

    private VoiceSynthesisParam parseVoiceParams(JsonNode vpNode) {
        if (vpNode == null || vpNode.isNull() || vpNode.isMissingNode()) return null;
        VoiceSynthesisParam vp = new VoiceSynthesisParam();
        if (vpNode.has("volume")) vp.setVolume(vpNode.path("volume").asInt(50));
        if (vpNode.has("speechRate")) vp.setSpeechRate((float) vpNode.path("speechRate").asDouble(1.0));
        if (vpNode.has("pitchRate")) vp.setPitchRate((float) vpNode.path("pitchRate").asDouble(1.0));
        if (vpNode.has("instruction")) vp.setInstruction(vpNode.path("instruction").asText(null));
        return vp;
    }

    private boolean isValidCandidate(GeneratorOutput output) {
        if (output == null || output.message == null || output.message.isBlank()) return false;
        String msg = output.message;
        return !msg.contains("无可用") && !msg.contains("无相关");
    }

    private WakeUpScoreResult parseScoreResult(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            int score = node.path("score").asInt(5);
            String reason = node.path("reason").asText("无理由");
            return new WakeUpScoreResult(score, reason);
        } catch (Exception e) {
            log.warn("解析评分 JSON 失败: {}", json, e);
            return new WakeUpScoreResult(5, "解析失败");
        }
    }

    private ArbiterDecision parseArbiterResult(String json, List<String> candidates,
                                                TimeContextTool.TimeContext timeContext) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String decision = node.path("decision").asText("direct");
            int selectedIndex = node.path("selectedIndex").asInt(0);
            String mergedMessage = node.path("mergedMessage").asText(null);

            switch (decision) {
                case "merge":
                    if (mergedMessage != null && !mergedMessage.isBlank()) {
                        return new ArbiterDecision(0, mergedMessage);
                    }
                    return pickCandidate(candidates, selectedIndex, timeContext);
                case "fallback":
                    String fallback = timeContext.greeting() + "～今天过得怎么样呀";
                    return new ArbiterDecision(0, fallback);
                default:
                    return pickCandidate(candidates, selectedIndex, timeContext);
            }
        } catch (Exception e) {
            log.warn("解析仲裁 JSON 失败，使用第一条候选: {}", json, e);
            String firstValid = null;
            for (String c : candidates) {
                if (c != null) {
                    firstValid = c;
                    break;
                }
            }
            return new ArbiterDecision(0, firstValid != null ? firstValid : timeContext.greeting() + "～今天过得怎么样呀");
        }
    }

    private ArbiterDecision pickCandidate(List<String> candidates, int preferredIndex,
                                           TimeContextTool.TimeContext timeContext) {
        int idx = Math.min(preferredIndex, candidates.size() - 1);
        String msg = candidates.get(idx);
        if (msg != null) {
            return new ArbiterDecision(idx, msg);
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) != null) {
                return new ArbiterDecision(i, candidates.get(i));
            }
        }
        return new ArbiterDecision(0, timeContext.greeting() + "～今天过得怎么样呀");
    }

    private static class GeneratorOutput {
        final String message;
        final VoiceSynthesisParam voiceParams;

        GeneratorOutput(String message, VoiceSynthesisParam voiceParams) {
            this.message = message;
            this.voiceParams = voiceParams;
        }
    }

    private static class ArbiterDecision {
        final int bestIndex;
        final String message;

        ArbiterDecision(int bestIndex, String message) {
            this.bestIndex = bestIndex;
            this.message = message;
        }
    }

    private void saveWakeUpMessageAsync(String userId, String content) {
        try {
            converMessageService.saveMessage(userId, "assistant", List.of(MessageContent.text(content)));
        } catch (Exception e) {
            log.warn("保存唤醒消息到数据库失败（不影响发送）：userId={}", userId, e);
        }
    }

    private boolean sendWakeUpWithVoice(String userId, String content, VoiceSynthesisParam voiceParams) {
        try {
            log.info("开始 TTS 合成：userId={}, textLength={}", userId, content.length());
            ByteBuffer audioBuffer;
            try {
                if (voiceParams != null) {
                    audioBuffer = voiceSynthesisService.synthesize(content, voiceParams);
                } else {
                    audioBuffer = voiceSynthesisService.synthesize(content, emotionService.getUserEmotion(userId));
                }
            } catch (Exception ttsEx) {
                log.warn("TTS 合成失败，fallback 纯文本：userId={}, error={}", userId, ttsEx.getMessage());
                userStateTool.recordWakeUp(userId);
                chatPushService.pushText(userId, content, true);
                log.info("唤醒消息已发送（纯文本）：userId={}, textLength={}", userId, content.length());
                return true;
            }

            if (audioBuffer == null) {
                log.warn("TTS 返回 null，fallback 纯文本：userId={}", userId);
                userStateTool.recordWakeUp(userId);
                chatPushService.pushText(userId, content, true);
                return true;
            }

            byte[] audioData = new byte[audioBuffer.remaining()];
            audioBuffer.get(audioData);
            log.info("TTS 合成完成：userId={}, audioSize={} bytes, useLLMParams={}",
                    userId, audioData.length, voiceParams != null);

            userStateTool.recordWakeUp(userId);
            chatPushService.pushText(userId, content, true);
            chatPushService.pushAudio(userId, audioData);

            log.info("唤醒消息已发送（文本+语音）：userId={}, textLength={}, audioSize={}",
                    userId, content.length(), audioData.length);
            return true;
        } catch (Exception e) {
            log.error("唤醒消息发送失败：userId={}", userId, e);
            return false;
        }
    }
}
