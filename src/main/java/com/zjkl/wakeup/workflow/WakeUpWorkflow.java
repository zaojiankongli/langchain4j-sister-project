package com.zjkl.wakeup.workflow;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.event.WakeUpSentEvent;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.VoiceSynthesisService;
import com.zjkl.settings.model.UserSettings;
import com.zjkl.settings.service.SettingsService;
import com.zjkl.user.service.UserProfileService;
import com.zjkl.wakeup.agent.WakeUpArbiterAgent;
import com.zjkl.wakeup.agent.WakeUpGenerator1Agent;
import com.zjkl.wakeup.agent.WakeUpGenerator2Agent;
import com.zjkl.wakeup.agent.WakeUpGenerator3Agent;
import com.zjkl.wakeup.agent.WakeUpScoreResult;
import com.zjkl.wakeup.agent.WakeUpScorer1Agent;
import com.zjkl.wakeup.agent.WakeUpScorer2Agent;
import com.zjkl.wakeup.agent.WakeUpScorer3Agent;
import com.zjkl.wakeup.arbiter.WakeUpArbiter;
import com.zjkl.wakeup.arbiter.WakeUpArbiter.ArbiterDecision;
import com.zjkl.wakeup.generator.WakeUpContentGenerator;
import com.zjkl.wakeup.generator.WakeUpContentGenerator.GeneratorOutput;
import com.zjkl.wakeup.scorer.WakeUpScorer;
import com.zjkl.wakeup.template.WakeUpPromptBuilder;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 单用户唤醒工作流。
 * 负责：过滤、上下文组装、生成、评分、仲裁、发送与记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpWorkflow {

    private final VoiceSynthesisService voiceSynthesisService;
    private final ChatPushService chatPushService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmotionService emotionService;
    private final SettingsService settingsService;
    private final UserStateTool userStateTool;
    private final UserProfileService userProfileService;
    private final PromptTemplateService promptTemplateService;
    private final WakeUpPromptBuilder promptBuilder;
    private final WakeUpContentGenerator contentGenerator;
    private final WakeUpScorer scorer;
    private final WakeUpArbiter arbiter;
    private final WakeUpTracker wakeUpTracker;
    private final WakeUpGenerator1Agent generator1Agent;
    private final WakeUpGenerator2Agent generator2Agent;
    private final WakeUpGenerator3Agent generator3Agent;
    private final WakeUpScorer1Agent scorer1Agent;
    private final WakeUpScorer2Agent scorer2Agent;
    private final WakeUpScorer3Agent scorer3Agent;
    private final WakeUpArbiterAgent arbiterAgent;

    private final Executor wakeupExecutor = Thread::startVirtualThread;

    public int processUserWakeUp(String userId, TimeContextTool.TimeContext timeContext) {
        boolean isDnd = userStateTool.isDoNotDisturb(userId);
        if (isDnd) {
            log.debug("用户处于免打扰状态，跳过唤醒：userId={}", userId);
            return 0;
        }

        Integer minutesSinceLastWakeup = userStateTool.getMinutesSinceLastWakeup(userId);
        if (minutesSinceLastWakeup < 0) {
            minutesSinceLastWakeup = 999;
        }

        UserSettings settings = settingsService.getSettings(userId);
        if (!settings.isProactiveEnabled()) {
            log.debug("用户已关闭主动推送，跳过唤醒：userId={}", userId);
            return 0;
        }
        if (minutesSinceLastWakeup < settings.getProactiveIntervalMin()) {
            log.debug("用户自定义冷却期内，跳过唤醒：userId={}, minutesSinceLastWakeup={}min, proactiveIntervalMin={}min",
                    userId, minutesSinceLastWakeup, settings.getProactiveIntervalMin());
            return 0;
        }

        Double silentHours = userStateTool.getSilentHours(userId);
        double probability = userStateTool.calculateWakeProbability(userId, silentHours, timeContext);
        log.info("概率计算：userId={}, probability={}, silentHours={}h",
                userId, String.format("%.3f", probability), String.format("%.1f", silentHours));

        if (Math.random() > probability) {
            log.debug("概率过滤未通过：userId={}, probability={}", userId, String.format("%.3f", probability));
            return 1;
        }

        UserStateTool.UserStateSnapshot state = userStateTool.buildStateSnapshot(
                userId, timeContext, isDnd, silentHours, minutesSinceLastWakeup);
        String anchorHint = promptBuilder.buildAnchorHint(state);

        String userName = "哥哥";
        String userHobbies = "";
        try {
            String[] chatProfile = userProfileService.getProfileForChat(userId);
            if (chatProfile != null) {
                userName = chatProfile[0] != null ? chatProfile[0] : userName;
                userHobbies = chatProfile[1] != null ? chatProfile[1] : "";
            }
        } catch (Exception e) {
            log.warn("获取用户画像失败，使用默认值: userId={}", userId, e);
        }

        EmotionalState currentEmotion = emotionService.getUserEmotion(userId);
        String moodLabel = emotionService.getUserMoodLabel(userId);
        String characterCore = promptTemplateService.render("character/core", Map.of(
                "pleasure", String.format("%.3f", currentEmotion.getPleasure()),
                "arousal", String.format("%.3f", currentEmotion.getArousal()),
                "dominance", String.format("%.3f", currentEmotion.getDominance()),
                "moodLabel", moodLabel != null ? moodLabel : ""
        ));

        String _cc = characterCore;
        String _tod = timeContext.timeOfDay();
        String _sm = timeContext.specialMoment();
        String _md = state.moodDescription();
        Double _ms = state.moodScore();
        Double _sh = state.silentHours();
        String _ah = anchorHint;
        String _un = userName;
        String _uh = userHobbies;
        String _uid = userId;

        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(
                () -> callGen1(generator1Agent, _cc, _tod, _sm, _md, _ms, _sh, _ah, _un, _uh, _uid),
                wakeupExecutor).exceptionally(e -> { log.warn("Generator1 失败: {}", e.getMessage()); return null; });
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(
                () -> callGen2(generator2Agent, _cc, _tod, _sm, _md, _ms, _sh, _ah, _un, _uh, _uid),
                wakeupExecutor).exceptionally(e -> { log.warn("Generator2 失败: {}", e.getMessage()); return null; });
        CompletableFuture<String> future3 = CompletableFuture.supplyAsync(
                () -> callGen3(generator3Agent, _cc, _tod, _sm, _md, _ms, _sh, _ah, _un, _uh, _uid),
                wakeupExecutor).exceptionally(e -> { log.warn("Generator3 失败: {}", e.getMessage()); return null; });

        String raw1 = future1.join();
        String raw2 = future2.join();
        String raw3 = future3.join();

        log.info("生成结果：userId={}, candidateLens=[{},{},{}]", userId,
                lengthOf(raw1), lengthOf(raw2), lengthOf(raw3));

        GeneratorOutput out1 = contentGenerator.parseGeneratorOutput(raw1);
        GeneratorOutput out2 = contentGenerator.parseGeneratorOutput(raw2);
        GeneratorOutput out3 = contentGenerator.parseGeneratorOutput(raw3);

        List<GeneratorOutput> candidates = new ArrayList<>();
        candidates.add(contentGenerator.isValidCandidate(out1) ? out1 : null);
        candidates.add(contentGenerator.isValidCandidate(out2) ? out2 : null);
        candidates.add(contentGenerator.isValidCandidate(out3) ? out3 : null);

        long validCount = candidates.stream().filter(c -> c != null).count();
        if (validCount == 0) {
            String fallbackMsg = promptBuilder.buildFallbackMessage(timeContext);
            log.info("无有效候选，使用 fallback：userId={}, msgLength={}", userId, fallbackMsg.length());
            boolean sent = sendWakeUpWithVoice(userId, fallbackMsg, null, settings);
            if (!sent) {
                return 2;
            }
            publishWakeUpSent(userId, fallbackMsg);
            return 3;
        }

        if (validCount == 1) {
            GeneratorOutput chosen = candidates.stream().filter(c -> c != null).findFirst()
                    .orElseThrow(() -> new IllegalStateException("有效候选列表为空，但 validCount==1 不匹配"));
            String msg = chosen.getMessage();
            log.info("仅有一条有效候选，直接使用：userId={}, msgLength={}", userId, msg.length());
            WakeUpTracker.SwapResult swapResult = wakeUpTracker.maybeSwap(
                    candidates.stream().map(c -> c != null ? c.getMessage() : null).toList(),
                    new int[]{0, 0, 0}, candidates.indexOf(chosen));
            msg = swapResult.getMessage();
            int actualSentIndex = swapResult.getActualSentIndex();
            GeneratorOutput selectedOutput = contentGenerator.selectOutput(candidates, actualSentIndex);
            VoiceSynthesisParam finalVoiceParams = selectedOutput != null ? selectedOutput.getVoiceParams() : null;
            boolean sent = sendWakeUpWithVoice(userId, msg, finalVoiceParams, settings);
            if (!sent) {
                return 2;
            }
            publishWakeUpSent(userId, msg);
            wakeUpTracker.recordSent(userId,
                    candidates.stream().map(c -> c != null ? c.getMessage() : null).toList(),
                    new int[]{0, 0, 0},
                    candidates.indexOf(chosen), actualSentIndex, msg);
            return 3;
        }

        log.info("开始并行评分：userId={}", userId);
        String candidateMsg1 = candidates.get(0) != null ? candidates.get(0).getMessage() : null;
        String candidateMsg2 = candidates.get(1) != null ? candidates.get(1).getMessage() : null;
        String candidateMsg3 = candidates.get(2) != null ? candidates.get(2).getMessage() : null;

        CompletableFuture<String> scoreFuture1 = CompletableFuture.supplyAsync(() -> {
            if (candidateMsg1 == null) {
                return "{\"score\":0,\"reason\":\"候选为空，跳过评分\"}";
            }
            return scorer1Agent.score(candidateMsg1, timeContext.timeOfDay(), timeContext.specialMoment(),
                    state.moodDescription(), state.moodScore(), userId);
        }, wakeupExecutor).exceptionally(e -> "{\"score\":5,\"reason\":\"评分失败\"}");

        CompletableFuture<String> scoreFuture2 = CompletableFuture.supplyAsync(() -> {
            if (candidateMsg2 == null) {
                return "{\"score\":0,\"reason\":\"候选为空，跳过评分\"}";
            }
            return scorer2Agent.score(candidateMsg2, timeContext.timeOfDay(), timeContext.specialMoment(),
                    state.moodDescription(), state.moodScore(), state.silentHours(), userId);
        }, wakeupExecutor).exceptionally(e -> "{\"score\":5,\"reason\":\"评分失败\"}");

        CompletableFuture<String> scoreFuture3 = CompletableFuture.supplyAsync(() -> {
            if (candidateMsg3 == null) {
                return "{\"score\":0,\"reason\":\"候选为空，跳过评分\"}";
            }
            return scorer3Agent.score(candidateMsg3, timeContext.timeOfDay(), timeContext.specialMoment(),
                    state.moodDescription(), state.moodScore(), anchorHint, state.silentHours(), userId);
        }, wakeupExecutor).exceptionally(e -> "{\"score\":5,\"reason\":\"评分失败\"}");

        String scoreJson1 = scoreFuture1.join();
        String scoreJson2 = scoreFuture2.join();
        String scoreJson3 = scoreFuture3.join();

        WakeUpScoreResult sr1 = scorer.parseScoreResult(scoreJson1);
        WakeUpScoreResult sr2 = scorer.parseScoreResult(scoreJson2);
        WakeUpScoreResult sr3 = scorer.parseScoreResult(scoreJson3);

        log.info("评分结果：userId={}, 评分1={}({}), 评分2={}({}), 评分3={}({})",
                userId, sr1.getScore(), sr1.getReason(), sr2.getScore(), sr2.getReason(), sr3.getScore(), sr3.getReason());

        String arbiterResult = arbiterAgent.decide(
                timeContext.timeOfDay(), timeContext.specialMoment(),
                state.moodDescription(), state.moodScore(), state.silentHours(), anchorHint,
                candidateMsg1, sr1.getScore(), sr1.getReason(),
                candidateMsg2, sr2.getScore(), sr2.getReason(),
                candidateMsg3, sr3.getScore(), sr3.getReason());

        log.info("仲裁结果：userId={}, resultLength={}", userId, lengthOf(arbiterResult));

        List<String> candidateMessages = Arrays.asList(candidateMsg1, candidateMsg2, candidateMsg3);
        ArbiterDecision decision = arbiter.parseArbiterResult(arbiterResult, candidateMessages, timeContext);
        int bestIndex = decision.getBestIndex();

        WakeUpTracker.SwapResult swapResult = wakeUpTracker.maybeSwap(candidateMessages,
                new int[]{sr1.getScore(), sr2.getScore(), sr3.getScore()}, bestIndex);

        String finalMessage = swapResult.getMessage();
        int validatedBestIndex = swapResult.getOriginalBestIndex();
        int actualSentIndex = swapResult.getActualSentIndex();
        GeneratorOutput selectedOutput = contentGenerator.selectOutput(candidates, actualSentIndex);
        VoiceSynthesisParam finalVoiceParams = selectedOutput != null ? selectedOutput.getVoiceParams() : null;

        boolean sent = sendWakeUpWithVoice(userId, finalMessage, finalVoiceParams, settings);
        if (!sent) {
            return 2;
        }

        publishWakeUpSent(userId, finalMessage);
        wakeUpTracker.recordSent(userId, candidateMessages,
                new int[]{sr1.getScore(), sr2.getScore(), sr3.getScore()},
                validatedBestIndex, actualSentIndex, finalMessage);
        return 3;
    }

    private void publishWakeUpSent(String userId, String content) {
        eventPublisher.publishEvent(new WakeUpSentEvent(userId, content, LocalDateTime.now()));
    }

    private boolean sendWakeUpWithVoice(String userId, String content, VoiceSynthesisParam voiceParams, UserSettings settings) {
        try {
            if (!settings.isTtsEnabled()) {
                log.info("用户已关闭 TTS，发送纯文本：userId={}", userId);
                chatPushService.pushText(userId, content, true);
                userStateTool.recordWakeUp(userId);
                return true;
            }

            log.info("开始 TTS 合成：userId={}, textLength={}", userId, content.length());
            ByteBuffer audioBuffer;
            try {
                if (voiceParams != null) {
                    int adjustedVolume = (int) Math.round(voiceParams.getVolume() * settings.getTtsVolume());
                    adjustedVolume = Math.max(0, Math.min(100, adjustedVolume));
                    voiceParams.setVolume(adjustedVolume);
                    voiceParams.setSpeechRate((float) settings.getTtsSpeed());
                    audioBuffer = voiceSynthesisService.synthesize(content, voiceParams);
                } else {
                    EmotionalState emotion = emotionService.getUserEmotion(userId);
                    VoiceParams vp = VoiceParams.fromEmotion(emotion);
                    VoiceSynthesisParam effectiveParams = new VoiceSynthesisParam();
                    int adjustedVolume = (int) Math.round(vp.getVolume() * settings.getTtsVolume());
                    adjustedVolume = Math.max(0, Math.min(100, adjustedVolume));
                    effectiveParams.setVolume(adjustedVolume);
                    effectiveParams.setSpeechRate((float) settings.getTtsSpeed());
                    effectiveParams.setPitchRate(vp.getPitchRate());
                    effectiveParams.setInstruction(vp.getInstruction());
                    audioBuffer = voiceSynthesisService.synthesize(content, effectiveParams);
                }
            } catch (Exception ttsEx) {
                log.warn("TTS 合成失败，fallback 纯文本：userId={}, error={}", userId, ttsEx.getMessage());
                chatPushService.pushText(userId, content, true);
                userStateTool.recordWakeUp(userId);
                log.info("唤醒消息已发送（纯文本）：userId={}, textLength={}", userId, content.length());
                return true;
            }

            if (audioBuffer == null) {
                log.warn("TTS 返回 null，fallback 纯文本：userId={}", userId);
                chatPushService.pushText(userId, content, true);
                userStateTool.recordWakeUp(userId);
                return true;
            }

            byte[] audioData = new byte[audioBuffer.remaining()];
            audioBuffer.get(audioData);
            log.info("TTS 合成完成：userId={}, audioSize={} bytes, useLLMParams={}", userId, audioData.length, voiceParams != null);

            chatPushService.pushText(userId, content, true);
            userStateTool.recordWakeUp(userId);
            try {
                chatPushService.pushAudio(userId, audioData);
            } catch (Exception audioEx) {
                log.warn("语音推送失败，保留已发送文本：userId={}, error={}", userId, audioEx.getMessage());
            }

            log.info("唤醒消息已发送（文本+语音）：userId={}, textLength={}, audioSize={}",
                    userId, content.length(), audioData.length);
            return true;
        } catch (Exception e) {
            log.error("唤醒消息发送失败：userId={}", userId, e);
            return false;
        }
    }

    private static String callGen1(WakeUpGenerator1Agent agent, String cc, String tod, String sm, String md, Double ms, Double sh, String ah, String un, String uh, String uid) {
        return agent.generate(cc, tod, sm, md, ms, sh, ah, un, uh, uid);
    }

    private static String callGen2(WakeUpGenerator2Agent agent, String cc, String tod, String sm, String md, Double ms, Double sh, String ah, String un, String uh, String uid) {
        return agent.generate(cc, tod, sm, md, ms, sh, ah, un, uh, uid);
    }

    private static String callGen3(WakeUpGenerator3Agent agent, String cc, String tod, String sm, String md, Double ms, Double sh, String ah, String un, String uh, String uid) {
        return agent.generate(cc, tod, sm, md, ms, sh, ah, un, uh, uid);
    }

    private static int lengthOf(String value) {
        return value != null ? value.length() : 0;
    }
}
