package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 情绪锚点语义化分析服务
 *
 * 负责调用 AI 模型为锚点事件生成语义化字段（标题、摘要、结束原因等）。
 * 独立于 EmotionAnchorService，使 AI 调用逻辑可单独测试和复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnchorSemanticService {

    private final QwenChatModel qwenChatModel;
    private final ConverMessageService converMessageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 为锚点事件生成语义化字段（调用 AI 模型）
     */
    public void generateSemanticFields(EmotionAnchorEvent event) {
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
                java.time.LocalDateTime endTime = event.getEndTime() != null ? event.getEndTime() : java.time.LocalDateTime.now();
                messages = converMessageService.getByTimeRange(
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

            String endTypeDesc = event.getEndType() != null ? event.getEndType().getDescription() : "未知";
            String endReasonTech = event.getEndReason() != null ? event.getEndReason() : "未知";
            String triggerReasonTech = event.getTriggerReason() != null ? event.getTriggerReason() : "未知";
            String prompt = buildPrompt(event, triggerReasonTech, endTypeDesc, endReasonTech, chatContext);

            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from("你是一个情绪分析助手。只输出 JSON，不要任何其他内容。"),
                            UserMessage.from(prompt))
                    .build());

            String result = response.aiMessage().text().trim();

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

            log.info("锚点事件语义化完成 - userId={}, eventTitle={}",
                    event.getUserId(), event.getEventTitle());
        } catch (Exception e) {
            log.warn("生成锚点事件语义化字段失败，使用默认值 - userId={}", event.getUserId(), e);
            applyDefaults(event);
        }
    }

    private String buildPrompt(EmotionAnchorEvent event, String triggerReasonTech,
                               String endTypeDesc, String endReasonTech, String chatContext) {
        return String.format("""
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
    }

    void applySemanticFields(EmotionAnchorEvent event, JsonNode jsonNode) {
        setIfPresent(event, "eventTitle", jsonNode, event::setEventTitle);
        setIfPresent(event, "triggerBehavior", jsonNode, event::setTriggerReason);
        setIfPresent(event, "highlightTraits", jsonNode, event::setHighlightTraits);
        setIfPresent(event, "summary", jsonNode, event::setSummary);
        setIfPresent(event, "endReason", jsonNode, event::setEndReason);
        setIfPresent(event, "aiReflection", jsonNode, event::setAiReflection);
    }

    private void setIfPresent(EmotionAnchorEvent event, String field, JsonNode jsonNode,
                              java.util.function.Consumer<String> setter) {
        String value = jsonNode.path(field).asText(null);
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    void applySemanticFieldsFromText(EmotionAnchorEvent event, String json) {
        setIfPresent(event, extractField(json, "eventTitle"), event::setEventTitle);
        setIfPresent(event, extractField(json, "triggerBehavior"), event::setTriggerReason);
        setIfPresent(event, extractField(json, "highlightTraits"), event::setHighlightTraits);
        setIfPresent(event, extractField(json, "summary"), event::setSummary);
        setIfPresent(event, extractField(json, "endReason"), event::setEndReason);
        setIfPresent(event, extractField(json, "aiReflection"), event::setAiReflection);
    }

    private void setIfPresent(EmotionAnchorEvent event, String value,
                              java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    void applyDefaults(EmotionAnchorEvent event) {
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
