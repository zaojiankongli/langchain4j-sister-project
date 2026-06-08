package com.zjkl.anchor.service;

import com.zjkl.anchor.monitor.AnchorMonitor;
import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.common.event.AnchorTriggeredEvent;
import com.zjkl.common.event.EmotionChangedEvent;
import com.zjkl.anchor.mapper.AnchorMapper;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.anchor.model.AnchorEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anchor 事件管理 — 触发 INSERT，结束 UPDATE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorEventService {

    private final AnchorMapper anchorMapper;
    private final AnchorMonitor anchorMonitor;
    private final AnchorSemanticService semanticService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmotionProperties emotionProperties;

    private final ConcurrentHashMap<String, Long> activeEventIds = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        anchorMonitor.setOnTrigger(this::handleAnchorTriggered);
        anchorMonitor.setOnEnd(this::handleAnchorEnded);

        int closed = anchorMapper.closeStaleEvents(emotionProperties.getAnchorMaxDurationMinutes());
        if (closed > 0) {
            log.info("已关闭 {} 个超时遗留的未结束锚点事件", closed);
        }

        List<AnchorEvent> openEvents = anchorMapper.selectOpenEvents();
        for (AnchorEvent event : openEvents) {
            if (event.getUserId() != null && event.getId() != null) {
                activeEventIds.put(event.getUserId(), event.getId());
            }
        }
        if (!openEvents.isEmpty()) {
            log.info("已重建 activeEventIds 映射 - 恢复 {} 个进行中的锚点事件", openEvents.size());
        }

        log.info("Anchor 服务初始化完成 - triggerThreshold={}, returnThreshold={}, silenceHours={}",
                AnchorMonitor.TRIGGER_THRESHOLD,
                AnchorMonitor.RETURN_THRESHOLD,
                AnchorMonitor.SILENCE_HOURS);
    }

    public void onEmotionChange(String userId, EmotionalState oldState, EmotionalState newState) {
        anchorMonitor.onEmotionChange(userId, oldState, newState);
    }

    @org.springframework.context.event.EventListener
    public void onEmotionChanged(EmotionChangedEvent event) {
        anchorMonitor.onEmotionChange(event.getUserId(), event.getOldState(), event.getNewState());
    }

    @Transactional(readOnly = true)
    public List<AnchorEvent> getUserEvents(String userId) {
        return anchorMapper.selectRecentByUserId(userId, 20);
    }

    @Transactional(readOnly = true)
    public AnchorEvent getLatestEvent(String userId) {
        List<AnchorEvent> events = anchorMapper.selectRecentByUserId(userId, 1);
        return events.isEmpty() ? null : events.get(0);
    }

    @Transactional(readOnly = true)
    public String getRecentAnchorSummary(String userId) {
        List<String> endTypes = anchorMapper.selectRecentEndTypes(userId, 5);
        if (endTypes.isEmpty()) {
            return "无历史锚点事件";
        }

        long positiveCount = endTypes.stream().filter("POSITIVE"::equals).count();
        long negativeCount = endTypes.stream().filter("NEGATIVE"::equals).count();

        return String.format("最近%d次锚点事件：%d次正面结束，%d次负面结束",
                endTypes.size(), positiveCount, negativeCount);
    }

    @Transactional(readOnly = true)
    public List<AnchorEvent> getEventsPaged(String userId, int offset, int limit, String beginDate, String endDate) {
        return anchorMapper.selectByUserIdPaged(userId, offset, limit, beginDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<String> getSuspenseTopics(String userId) {
        return anchorMapper.selectRecentNegativeTopics(userId, 2);
    }

    public void handleAnchorTriggered(AnchorEvent event) {
        event.setCreatedAt(LocalDateTime.now());
        anchorMapper.insert(event);
        activeEventIds.put(event.getUserId(), event.getId());
        log.info("锚点事件已持久化(trigger) - id={}, userId={}, deltaP={}",
                event.getId(), event.getUserId(), event.getDeltaPleasure());
        eventPublisher.publishEvent(new AnchorTriggeredEvent(event.getUserId(), event, LocalDateTime.now()));
    }

    @Async("llmTaskExecutor")
    public void handleAnchorEnded(AnchorEvent event) {
        try {
            semanticService.generateSemanticFields(event);

            Long currentId = activeEventIds.get(event.getUserId());
            Long activeId = null;
            if (currentId != null && event.getId() != null && currentId.equals(event.getId())) {
                activeId = activeEventIds.remove(event.getUserId());
            } else if (currentId == null) {
                activeId = event.getId();
            } else {
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

    private void fallbackInsert(AnchorEvent event) {
        event.setCreatedAt(LocalDateTime.now());
        anchorMapper.insert(event);
        log.info("锚点事件已持久化(fallback insert) - id={}, userId={}, endType={}, duration={}s",
                event.getId(), event.getUserId(), event.getEndType(), event.getDurationSeconds());
    }
}
