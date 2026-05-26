package com.zjkl.emotion.listener;

import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.common.event.AnchorTriggeredEvent;
import com.zjkl.common.event.EmotionChangedEvent;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 情绪领域事件监听器 — 记录日志 + 副作用处理
 *
 * 职责：
 * - 记录各类情绪事件日志
 * - 锚点结束时将摘要注入 Redis 聊天历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionEventListener {

    private final StringRedisTemplate stringRedisTemplate;

    @EventListener
    public void onAnchorTriggered(AnchorTriggeredEvent event) {
        log.info("[EVENT] 锚点触发 - userId={}, eventId={}, timestamp={}",
                event.getUserId(), event.getEvent().getId(), event.getTimestamp());
    }

    @EventListener
    public void onAnchorEnded(AnchorEndedEvent event) {
        log.info("[EVENT] 锚点结束 - userId={}, eventId={}, endType={}, duration={}s, timestamp={}",
                event.getUserId(), event.getEvent().getId(), event.getEndType(),
                event.getEvent().getDurationSeconds(), event.getTimestamp());

        // 将锚点摘要注入 Redis 聊天历史
        injectAnchorToChatHistory(event.getUserId(), event.getEvent());
    }

    @EventListener
    public void onEmotionChanged(EmotionChangedEvent event) {
        log.info("[EVENT] 情绪变化 - userId={}, oldPleasure={}, newPleasure={}, timestamp={}",
                event.getUserId(),
                event.getOldState() != null ? event.getOldState().getFormattedPleasure() : "N/A",
                event.getNewState() != null ? event.getNewState().getFormattedPleasure() : "N/A",
                event.getTimestamp());
    }

    /**
     * 将锚点摘要注入 Redis 聊天历史，使大模型在后续对话中感知情绪锚点
     */
    private void injectAnchorToChatHistory(String userId, EmotionAnchorEvent event) {
        String historyKey = "chat:history:" + userId;
        String anchorEntry = "system: 【情绪锚点】" + event.getEventTitle()
                + "。" + event.getSummary()
                + (event.getEndType() == EmotionAnchorEvent.EndType.NEGATIVE ? "（情绪偏低，需要关注）" : "");
        try {
            stringRedisTemplate.opsForList().rightPush(historyKey, anchorEntry);
            stringRedisTemplate.opsForList().trim(historyKey, -200, -1);
            log.debug("锚点聊天历史注入成功 - userId={}", userId);
        } catch (Exception e) {
            log.warn("锚点注入聊天历史失败: userId={}", userId, e);
        }
    }
}
