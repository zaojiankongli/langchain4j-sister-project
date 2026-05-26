package com.zjkl.emotion.monitor;

import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 情绪锚点监测 — 超阈值触发，沉默/回归/超时结束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnchorMonitor {

    public static final double TRIGGER_THRESHOLD = 0.15;
    public static final double RETURN_THRESHOLD = 0.05;
    public static final int SILENCE_HOURS = 2;

    private final EmotionProperties emotionProperties;

    private static final int CLEANUP_HOURS = 2;

    private final ConcurrentHashMap<String, MonitorState> monitors = new ConcurrentHashMap<>();

    private Consumer<EmotionAnchorEvent> onTriggerCallback;
    private Consumer<EmotionAnchorEvent> onEndCallback;

    private static class MonitorState {
        enum Status { IDLE, MONITORING }

        volatile Status status = Status.IDLE;
        volatile double startPleasure;
        volatile double peakPleasure;
        volatile double startArousal;
        volatile double peakArousal;
        volatile LocalDateTime startTime;
        volatile LocalDateTime lastMsgTime;
    }

    public void setOnTrigger(Consumer<EmotionAnchorEvent> callback) {
        this.onTriggerCallback = callback;
    }

    public void setOnEnd(Consumer<EmotionAnchorEvent> callback) {
        this.onEndCallback = callback;
    }



    /**
     * 情绪变化入口
     */
    public void onEmotionChange(String userId, EmotionalState oldState, EmotionalState newState) {
        if (oldState == null || newState == null) {
            return;
        }

        MonitorState state = monitors.computeIfAbsent(userId, k -> new MonitorState());
        double oldP = oldState.getPleasure();
        double newP = newState.getPleasure();
        double oldA = oldState.getArousal();
        double newA = newState.getArousal();
        double delta = Math.abs(newP - oldP);

        synchronized (state) {
            switch (state.status) {
                case IDLE -> {
                    if (delta > TRIGGER_THRESHOLD) {
                        triggerEvent(userId, state, oldP, newP, oldA, newA);
                    }
                    state.lastMsgTime = LocalDateTime.now();
                }
                case MONITORING -> {
                    if (newA > state.peakArousal) {
                        state.peakArousal = newA;
                    }

                    if (isSilent(state)) {
                        endEvent(userId, state, newP, newA, "用户沉默超过" + SILENCE_HOURS + "小时且愉悦度低于正常值");
                    }
                    else if (Math.abs(newP - state.startPleasure) < RETURN_THRESHOLD) {
                        endEvent(userId, state, newP, newA, "情绪平稳回归基准");
                    }
                    else if (isTimeout(state)) {
                        endEvent(userId, state, newP, newA, "情绪持续偏移" + emotionProperties.getAnchorMaxDurationMinutes() + "分钟且愉悦度低于正常值");
                    }
                    else if (newP > state.peakPleasure) {
                        state.peakPleasure = newP;
                    }
                }
            }
        }
    }

    public String getStatus(String userId) {
        MonitorState state = monitors.get(userId);
        if (state == null) return "IDLE";
        synchronized (state) {
            return state.status.name();
        }
    }

    public int getActiveCount() {
        return (int) monitors.values().stream()
                .filter(s -> {
                    synchronized (s) {
                        return s.status == MonitorState.Status.MONITORING;
                    }
                })
                .count();
    }

    /**
     * 锚点上下文文本
     */
    public String getAnchorContext(String userId) {
        MonitorState state = monitors.get(userId);
        if (state == null) return null;
        synchronized (state) {
            if (state.status != MonitorState.Status.MONITORING) {
                return null;
            }
            long minutes = state.startTime != null
                    ? Duration.between(state.startTime, LocalDateTime.now()).toMinutes()
                    : 0;
            return String.format("有正在进行的情绪锚点事件，已持续%d分钟，起始愉悦度=%.2f，峰值愉悦度=%.2f，当前状态=%s",
                    minutes, state.startPleasure, state.peakPleasure,
                    state.startPleasure >= 0 ? "正面波动" : "负面波动");
        }
    }

    private void triggerEvent(String userId, MonitorState state, double oldP, double newP, double oldA, double newA) {
        state.status = MonitorState.Status.MONITORING;
        state.startPleasure = oldP;
        state.peakPleasure = newP;
        state.startArousal = oldA;
        state.peakArousal = newA;
        state.startTime = LocalDateTime.now();
        state.lastMsgTime = LocalDateTime.now();

        log.info("锚点事件触发 - userId={}, deltaP={}, startP={}, newP={}, startA={}, newA={}",
                userId, newP - oldP, oldP, newP, oldA, newA);

        if (onTriggerCallback != null) {
            EmotionAnchorEvent event = EmotionAnchorEvent.builder()
                    .userId(userId)
                    .startTime(state.startTime)
                    .startPleasure(toBigDecimal(oldP))
                    .peakPleasure(toBigDecimal(newP))
                    .deltaPleasure(toBigDecimal(newP - oldP))
                    .startArousal(toBigDecimal(oldA))
                    .peakArousal(toBigDecimal(newA))
                    .deltaArousal(toBigDecimal(newA - oldA))
                    .triggerReason("愉悦度变化 " + String.format("%.4f", Math.abs(newP - oldP)) + " 超过阈值 " + TRIGGER_THRESHOLD)
                    .build();
            onTriggerCallback.accept(event);
        }
    }

    /**
     * 结束锚点事件
     */
    private void endEvent(String userId, MonitorState state, double endP, double endA, String endReason) {
        LocalDateTime endTime = LocalDateTime.now();
        boolean isPositiveEnd = endP > RETURN_THRESHOLD;
        EmotionAnchorEvent.EndType endType = isPositiveEnd
                ? EmotionAnchorEvent.EndType.POSITIVE
                : EmotionAnchorEvent.EndType.NEGATIVE;

        log.info("锚点事件结束 - userId={}, endType={}, endReason={}, duration={}s",
                userId, endType, endReason,
                Duration.between(state.startTime, endTime).getSeconds());

        state.status = MonitorState.Status.IDLE;

        if (onEndCallback != null) {
            EmotionAnchorEvent event = EmotionAnchorEvent.builder()
                    .userId(userId)
                    .startTime(state.startTime)
                    .endTime(endTime)
                    .startPleasure(toBigDecimal(state.startPleasure))
                    .peakPleasure(toBigDecimal(state.peakPleasure))
                    .endPleasure(toBigDecimal(endP))
                    .deltaPleasure(toBigDecimal(state.peakPleasure - state.startPleasure))
                    .startArousal(toBigDecimal(state.startArousal))
                    .peakArousal(toBigDecimal(state.peakArousal))
                    .endArousal(toBigDecimal(endA))
                    .deltaArousal(toBigDecimal(endA - state.startArousal))
                    .endType(endType)
                    .endReason(endReason)
                    .triggerReason("愉悦度变化 " + String.format("%.4f", Math.abs(state.peakPleasure - state.startPleasure)) + " 超过阈值 " + TRIGGER_THRESHOLD)
                    .build();
            event.calculateDuration();
            onEndCallback.accept(event);
        }
    }

    private boolean isSilent(MonitorState state) {
        if (state.lastMsgTime == null) {
            return false;
        }
        return Duration.between(state.lastMsgTime, LocalDateTime.now()).toHours() >= SILENCE_HOURS;
    }

    private boolean isTimeout(MonitorState state) {
        if (state.startTime == null) {
            return false;
        }
        return Duration.between(state.startTime, LocalDateTime.now()).toMinutes() >= emotionProperties.getAnchorMaxDurationMinutes();
    }

    /**
     * 定期清理过期状态
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupIdleMonitors() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(CLEANUP_HOURS);
        int before = monitors.size();

        monitors.entrySet().removeIf(entry -> {
            MonitorState state = entry.getValue();
            return state.status == MonitorState.Status.IDLE
                    && state.lastMsgTime != null
                    && state.lastMsgTime.isBefore(threshold);
        });

        int removed = before - monitors.size();
        if (removed > 0) {
            log.debug("清理过期 Monitor - 清理 {}, 剩余 {}", removed, monitors.size());
        }
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
