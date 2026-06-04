package com.zjkl.emotion.service;

import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.emotion.mapper.EmotionAnchorMapper;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.common.event.AnchorTriggeredEvent;
import com.zjkl.common.event.EmotionChangedEvent;
import com.zjkl.emotion.monitor.EmotionAnchorMonitor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情绪锚点事件管理 — 触发 INSERT，结束 UPDATE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnchorService {

    private final EmotionAnchorMapper anchorMapper;
    private final EmotionAnchorMonitor anchorMonitor;
    private final EmotionAnchorSemanticService semanticService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmotionProperties emotionProperties;

    private final ConcurrentHashMap<String, Long> activeEventIds = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        anchorMonitor.setOnTrigger(this::handleAnchorTriggered);
        anchorMonitor.setOnEnd(this::handleAnchorEnded);

        // 服务重启后，关闭超过最大持续时长的残留锚点事件
        int closed = anchorMapper.closeStaleEvents(emotionProperties.getAnchorMaxDurationMinutes());
        if (closed > 0) {
            log.info("已关闭 {} 个超时遗留的未结束锚点事件", closed);
        }

        // 从 DB 查询未关闭的事件来重建 activeEventIds 映射
        List<EmotionAnchorEvent> openEvents = anchorMapper.selectOpenEvents();
        for (EmotionAnchorEvent event : openEvents) {
            if (event.getUserId() != null && event.getId() != null) {
                activeEventIds.put(event.getUserId(), event.getId());
            }
        }
        if (!openEvents.isEmpty()) {
            log.info("已重建 activeEventIds 映射 - 恢复 {} 个进行中的锚点事件", openEvents.size());
        }

        log.info("情绪锚点服务初始化完成 - triggerThreshold={}, returnThreshold={}, silenceHours={}",
                EmotionAnchorMonitor.TRIGGER_THRESHOLD,
                EmotionAnchorMonitor.RETURN_THRESHOLD,
                EmotionAnchorMonitor.SILENCE_HOURS);
    }

    public void onEmotionChange(String userId, EmotionalState oldState, EmotionalState newState) {
        anchorMonitor.onEmotionChange(userId, oldState, newState);
        eventPublisher.publishEvent(new EmotionChangedEvent(userId, oldState, newState, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<EmotionAnchorEvent> getUserEvents(String userId) {
        return anchorMapper.selectRecentByUserId(userId, 20);
    }

    @Transactional(readOnly = true)
    public EmotionAnchorEvent getLatestEvent(String userId) {
        List<EmotionAnchorEvent> events = anchorMapper.selectRecentByUserId(userId, 1);
        return events.isEmpty() ? null : events.get(0);
    }

    @Transactional(readOnly = true)
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

    /**
     * 分页查询用户的锚点事件（供 anchor 模块使用，避免跨模块 mapper 访问）
     */
    @Transactional(readOnly = true)
    public List<EmotionAnchorEvent> getEventsPaged(String userId, int offset, int limit, String beginDate, String endDate) {
        return anchorMapper.selectByUserIdPaged(userId, offset, limit, beginDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<String> getSuspenseTopics(String userId) {
        return anchorMapper.selectRecentNegativeTopics(userId, 2);
    }

    /**
     * 触发：INSERT（同步执行，确保 activeEventIds 在 handleAnchorEnded 之前写入）
     * 异常向上抛出，由调用方（EmotionAnchorMonitor）处理
     */
    public void handleAnchorTriggered(EmotionAnchorEvent event) {
        event.setCreatedAt(LocalDateTime.now());
        anchorMapper.insert(event);
        activeEventIds.put(event.getUserId(), event.getId());
        log.info("锚点事件已持久化(trigger) - id={}, userId={}, deltaP={}",
                event.getId(), event.getUserId(), event.getDeltaPleasure());
        eventPublisher.publishEvent(new AnchorTriggeredEvent(event.getUserId(), event, LocalDateTime.now()));
    }

    /**
     * 结束：UPDATE
     */
    @Async
    public void handleAnchorEnded(EmotionAnchorEvent event) {
        try {
            semanticService.generateSemanticFields(event);

            // Conditional remove: avoid deleting a newer event's ID that may have
            // been written by handleAnchorTriggered after this end-event was created.
            Long currentId = activeEventIds.get(event.getUserId());
            Long activeId = null;
            if (currentId != null && event.getId() != null && currentId.equals(event.getId())) {
                activeId = activeEventIds.remove(event.getUserId());
            } else if (currentId == null) {
                // Already removed, try to use event's own ID
                activeId = event.getId();
            } else {
                // currentId != event.getId() — a newer event has started;
                // do NOT remove the newer ID. Fall through to use event's own ID.
                activeId = event.getId();
            }

            boolean updated = false;
            if (activeId != null) {
                event.setId(activeId);
                updated = anchorMapper.updateEndFields(event) > 0;
            }
            if (!updated) {
                fallbackInsert(event);
            }

            log.info("锚点事件已更新(end) - id={}, userId={}, endType={}, endReason={}, duration={}s",
                    event.getId(), event.getUserId(), event.getEndType(), event.getEndReason(), event.getDurationSeconds());
            eventPublisher.publishEvent(new AnchorEndedEvent(event.getUserId(), event, event.getEndType(), LocalDateTime.now()));
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


}
