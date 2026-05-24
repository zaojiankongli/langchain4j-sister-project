package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.mapper.ConverMessageMapper;
import com.zjkl.emotion.mapper.EmotionAnchorMapper;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import com.zjkl.emotion.monitor.EmotionAnchorMonitor;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 情绪锚点事件管理 — 触发 INSERT，结束 UPDATE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnchorService {

    private final EmotionAnchorMapper anchorMapper;
    private final EmotionAnchorMonitor anchorMonitor;
    private final QwenChatModel qwenChatModel;
    private final ConverMessageMapper converMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Long> activeEventIds = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        anchorMonitor.setOnTrigger(this::handleAnchorTriggered);
        anchorMonitor.setOnEnd(this::handleAnchorEnded);

        log.info("情绪锚点服务初始化完成 - triggerThreshold={}, returnThreshold={}, silenceHours={}",
                EmotionAnchorMonitor.TRIGGER_THRESHOLD,
                EmotionAnchorMonitor.RETURN_THRESHOLD,
                EmotionAnchorMonitor.SILENCE_HOURS);
    }

    public void onEmotionChange(String userId, EmotionalState oldState, EmotionalState newState) {
        anchorMonitor.onEmotionChange(userId, oldState, newState);
    }

    public List<EmotionAnchorEvent> getUserEvents(String userId) {
        return anchorMapper.selectRecentByUserId(userId, 20);
    }

    public EmotionAnchorEvent getLatestEvent(String userId) {
        List<EmotionAnchorEvent> events = anchorMapper.selectRecentByUserId(userId, 1);
        return events.isEmpty() ? null : events.get(0);
    }

    public String getRecentAnchorSummary(String userId) {
        List<String> endTypes = anchorMapper.selectRecentEndTypes(userId, 5);
        if (endTypes.isEmpty()) {
            return "无历史锚点事件";
        }

        long positiveCount = endTypes.stream()
                .filter("POSITIVE"::equals)
                .count();
        long negativeCount = endTypes.size() - positiveCount;

        return String.format("最近%d次锚点事件：%d次正面结束，%d次负面结束",
                endTypes.size(), positiveCount, negativeCount);
    }

    public List<String> getSuspenseTopics(String userId) {
        return anchorMapper.selectRecentNegativeTopics(userId, 2);
    }

    /**
     * 触发：INSERT
     */
    @Async
    public void handleAnchorTriggered(EmotionAnchorEvent event) {
        try {
            event.setCreatedAt(LocalDateTime.now());
            anchorMapper.insert(event);
            activeEventIds.put(event.getUserId(), event.getId());
            log.info("锚点事件已持久化(trigger) - id={}, userId={}, deltaP={}",
                    event.getId(), event.getUserId(), event.getDeltaPleasure());
        } catch (Exception e) {
            log.error("锚点事件持久化失败(trigger) - userId={}", event.getUserId(), e);
        }
    }

    /**
     * 结束：UPDATE
     */
    @Async
    public void handleAnchorEnded(EmotionAnchorEvent event) {
        try {
            generateSemanticFields(event);

            // 回写数据库
            Long activeId = activeEventIds.remove(event.getUserId());
            boolean updated = false;
            if (activeId != null) {
                event.setId(activeId);
                updated = anchorMapper.updateEndFields(event) > 0;
            }
            if (!updated) {
                fallbackInsert(event);
            }

            // 注入 Redis 聊天历史
            String userId = event.getUserId();
            String historyKey = "chat:history:" + userId;
            String anchorEntry = "system: 【情绪锚点】" + event.getEventTitle()
                    + "。" + event.getSummary()
                    + (event.getEndType() == EmotionAnchorEvent.EndType.NEGATIVE ? "（情绪偏低，需要关注）" : "");
            try {
                stringRedisTemplate.opsForList().rightPush(historyKey, anchorEntry);
                stringRedisTemplate.opsForList().trim(historyKey, -200, -1);
            } catch (Exception e) {
                log.warn("锚点注入聊天历史失败: userId={}", userId, e);
            }

            log.info("锚点事件已更新(end) - id={}, userId={}, endType={}, endReason={}, duration={}s",
                    event.getId(), userId, event.getEndType(), event.getEndReason(), event.getDurationSeconds());
        } catch (Exception e) {
            log.error("锚点事件结束处理失败 - userId={}", event.getUserId(), e);
        }
    }

    private void fallbackInsert(EmotionAnchorEvent event) {
        event.setCreatedAt(LocalDateTime.now());
        anchorMapper.insert(event);
        log.info("锚点事件已持久化(fallback insert) - id={}, userId={}, endType={}, duration={}s",
                event.getId(), event.getUserId(), event.getEndType(), event.getDurationSeconds());
    }

    /**
     * 调用模型生成语义化字段
     */
    private void generateSemanticFields(EmotionAnchorEvent event) {
        String userId = event.getUserId();
        String cacheKey = "anchor:summary:" + userId + ":" + LocalDate.now();
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                applyDefaults(event);
                log.info("锚点摘要使用缓存: userId={}", userId);
                return;
            }
        } catch (Exception e) {
            log.warn("锚点摘要缓存读取失败: userId={}", userId, e);
        }

        try {
            List<ConverMessage> messages = List.of();
            if (event.getStartTime() != null) {
                LocalDateTime endTime = event.getEndTime() != null ? event.getEndTime() : LocalDateTime.now();
                messages = converMessageMapper.selectByUserIdAndTimeRange(
                        event.getUserId(), event.getStartTime(), endTime);
            }

            List<ConverMessage> contextMessages = messages.size() > 20
                    ? messages.subList(messages.size() - 20, messages.size())
                    : messages;
            String chatContext = contextMessages.isEmpty()
                    ? "无对话记录"
                    : contextMessages.stream()
                    .map(m -> m.getRole() + ": " + extractText(m))
                    .collect(Collectors.joining("\n"));

            // 构建 prompt
            String endTypeDesc = event.getEndType() != null ? event.getEndType().getDescription() : "未知";
            String endReasonTech = event.getEndReason() != null ? event.getEndReason() : "未知";
            String triggerReasonTech = event.getTriggerReason() != null ? event.getTriggerReason() : "未知";
            String prompt = String.format("""
                    以下是一个情绪锚点事件的信息：

                    ## 事件基本信息
                    - 技术触发原因：%s
                    - 愉悦度变化：%.3f
                    - 唤醒度变化：%.3f
                    - 持续时长：%d秒
                    - 结束类型：%s
                    - 技术结束原因：%s

                    ## 期间对话记录
                    %s

                    请根据对话记录，用 JSON 格式输出以下六个字段：

                    {
                      "eventTitle": "事件短标题（10-20字，概括事件主题，如'用户抱怨工作压力大'）",
                      "triggerBehavior": "用户行为描述（15字内，描述用户做了什么触发了情绪变化，如'你主动关心了她'）",
                      "highlightTraits": "情绪变化描述（15字内，描述AI情绪发生了什么变化，如'依赖感增加了'）",
                      "summary": "事件详细摘要（至少200字，详细描述事件经过、用户情绪变化、AI的回应和感受）",
                      "endReason": "结束原因语义描述（15字内，概括事件如何结束，如'情绪平稳回归基准'或'用户沉默超过2小时'）",
                      "aiReflection": "AI反思（20字内，以AI视角表达感受，如'希望下次能帮到你'）"
                    }
                    """,
                    triggerReasonTech,
                    event.getDeltaPleasure() != null ? event.getDeltaPleasure().doubleValue() : 0,
                    event.getDeltaArousal() != null ? event.getDeltaArousal().doubleValue() : 0,
                    event.getDurationSeconds() != null ? event.getDurationSeconds() : 0,
                    endTypeDesc,
                    endReasonTech,
                    chatContext);

            // 调用模型
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from("你是一个情绪分析助手。只输出 JSON，不要任何其他内容。"),
                            UserMessage.from(prompt))
                    .build());

            String result = response.aiMessage().text().trim();

            // 解析返回 JSON
            try {
                JsonNode jsonNode = objectMapper.readTree(result);
                applySemanticFields(event, jsonNode);
            } catch (Exception jsonEx) {
                log.warn("Jackson 解析失败，降级为手工提取: {}", result);
                String cleanJson = extractJson(result);
                applySemanticFieldsFromText(event, cleanJson);
            }

            try {
                stringRedisTemplate.opsForValue().set(cacheKey, "1", Duration.ofHours(24));
            } catch (Exception e) {
                log.warn("锚点摘要缓存写入失败: userId={}", userId, e);
            }

            log.info("锚点事件语义化完成 - userId={}, eventTitle={}, triggerReason={}, endReason={}, highlightTraits={}",
                    event.getUserId(), event.getEventTitle(), event.getTriggerReason(),
                    event.getEndReason(), event.getHighlightTraits());
        } catch (Exception e) {
            log.warn("生成锚点事件语义化字段失败，使用默认值 - userId={}", event.getUserId(), e);
            applyDefaults(event);
        }
    }

    private void applySemanticFields(EmotionAnchorEvent event, JsonNode jsonNode) {
        String eventTitle = jsonNode.path("eventTitle").asText(null);
        String triggerBehavior = jsonNode.path("triggerBehavior").asText(null);
        String highlightTraits = jsonNode.path("highlightTraits").asText(null);
        String summary = jsonNode.path("summary").asText(null);
        String endReason = jsonNode.path("endReason").asText(null);
        String aiReflection = jsonNode.path("aiReflection").asText(null);

        if (eventTitle != null && !eventTitle.isBlank()) {
            event.setEventTitle(eventTitle);
        }
        if (triggerBehavior != null && !triggerBehavior.isBlank()) {
            event.setTriggerReason(triggerBehavior);
        }
        if (highlightTraits != null && !highlightTraits.isBlank()) {
            event.setHighlightTraits(highlightTraits);
        }
        if (summary != null && !summary.isBlank()) {
            event.setSummary(summary);
        }
        if (endReason != null && !endReason.isBlank()) {
            event.setEndReason(endReason);
        }
        if (aiReflection != null && !aiReflection.isBlank()) {
            event.setAiReflection(aiReflection);
        }
    }

    private void applySemanticFieldsFromText(EmotionAnchorEvent event, String json) {
        String eventTitle = extractField(json, "eventTitle");
        String triggerBehavior = extractField(json, "triggerBehavior");
        String highlightTraits = extractField(json, "highlightTraits");
        String summary = extractField(json, "summary");
        String endReason = extractField(json, "endReason");
        String aiReflection = extractField(json, "aiReflection");

        if (eventTitle != null && !eventTitle.isBlank()) event.setEventTitle(eventTitle);
        if (triggerBehavior != null && !triggerBehavior.isBlank()) event.setTriggerReason(triggerBehavior);
        if (highlightTraits != null && !highlightTraits.isBlank()) event.setHighlightTraits(highlightTraits);
        if (summary != null && !summary.isBlank()) event.setSummary(summary);
        if (endReason != null && !endReason.isBlank()) event.setEndReason(endReason);
        if (aiReflection != null && !aiReflection.isBlank()) event.setAiReflection(aiReflection);
    }

    private void applyDefaults(EmotionAnchorEvent event) {
        if (event.getEventTitle() == null) event.setEventTitle("情绪锚点事件");
        if (event.getSummary() == null) event.setSummary("无摘要");
        if (event.getTriggerReason() == null) event.setTriggerReason("情绪波动触发");
        if (event.getHighlightTraits() == null) event.setHighlightTraits("情绪波动");
        if (event.getEndReason() == null) event.setEndReason("事件自然结束");
        String endType = event.getEndType() != null ? event.getEndType().name() : "";
        if (event.getAiReflection() == null) {
            event.setAiReflection("NEGATIVE".equals(endType)
                    ? "希望下次能帮到你"
                    : "真好，为你感到开心");
        }
    }

    private String extractText(ConverMessage msg) {
        if (msg.getContents() == null) return "";
        return msg.getContents().stream()
                .filter(c -> "text".equals(c.getType()))
                .map(c -> c.getText() != null ? c.getText() : "")
                .collect(Collectors.joining(" "));
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String extractField(String json, String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx);
        if (colonIdx < 0) return null;
        int valueStart = json.indexOf('"', colonIdx);
        if (valueStart < 0) return null;
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return json.substring(valueStart + 1, valueEnd);
    }
}
