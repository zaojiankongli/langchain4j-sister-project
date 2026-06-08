package com.zjkl.anchor.monitor;

import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.anchor.model.AnchorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Anchor 锚点监测 — 超阈值触发，沉默/回归/超时结束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnchorMonitor {

    public static final double TRIGGER_THRESHOLD = 0.15;
    public static final double RETURN_THRESHOLD = 0.05;
    public static final int SILENCE_HOURS = 2;

    private final EmotionProperties emotionProperties;

    private static final int CLEANUP_HOURS = 2;
    private static final int MAX_MONITOR_CAPACITY = 10000;

    private final ConcurrentHashMap<String, MonitorState> monitors = new ConcurrentHashMap<>();

    private Consumer<AnchorEvent> onTriggerCallback;
    private Consumer<AnchorEvent> onEndCallback;

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

    public void setOnTrigger(Consumer<AnchorEvent> callback) {
        this.onTriggerCallback = callback;
    }

    public void setOnEnd(Consumer<AnchorEvent> callback) {
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

        Runnable triggerCallback = null;
        Runnable endCallback = null;

        synchronized (state) {
            switch (state.status) {
                case IDLE -> {
                    if (delta > TRIGGER_THRESHOLD) {
                        triggerCallback = prepareTriggerEvent(userId, state, oldP, newP, oldA, newA);
                    }
                    state.lastMsgTime = LocalDateTime.now();
                }
                case MONITORING -> {
                    state.lastMsgTime = LocalDateTime.now();

                    if (newA > state.peakArousal) {
                        state.peakArousal = newA;
                    }

                    if (isSilent(state)) {
                        endCallback = prepareEndEvent(userId, state, newP, newA, "用户沉默超过" + SILENCE_HOURS + "小时且愉悦度低于正常值");
                    }
                    else if (Math.abs(newP - state.startPleasure) < RETURN_THRESHOLD) {
                        endCallback = prepareEndEvent(userId, state, newP, newA, "情绪平稳回归基准");
                    }
                    else if (isTimeout(state)) {
                        endCallback = prepareEndEvent(userId, state, newP, newA, "情绪持续偏移" + emotionProperties.getAnchorMaxDurationMinutes() + "分钟且愉悦度低于正常值");
                    }
                    else if (newP > state.peakPleasure) {
                        state.peakPleasure = newP;
                    }
                }
            }
        }

        if (triggerCallback != null) triggerCallback.run();
        if (endCallback != null) endCallback.run();
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

    private Runnable prepareTriggerEvent(String userId, MonitorState state, double oldP, double newP, double oldA, double newA) {
        LocalDateTime now = LocalDateTime.now();
        log.info("锚点事件触发 - userId={}, deltaP={}, startP={}, newP={}, startA={}, newA={}",
                userId, newP - oldP, oldP, newP, oldA, newA);

        AnchorEvent event = AnchorEvent.builder()
                .userId(userId)
                .startTime(now)
                .startPleasure(toBigDecimal(oldP))
                .peakPleasure(toBigDecimal(newP))
                .deltaPleasure(toBigDecimal(newP - oldP))
                .startArousal(toBigDecimal(oldA))
                .peakArousal(toBigDecimal(newA))
                .deltaArousal(toBigDecimal(newA - oldA))
                .triggerReason("愉悦度变化 " + String.format("%.4f", Math.abs(newP - oldP)) + " 超过阈值 " + TRIGGER_THRESHOLD)
                .build();

        return () -> {
            try {
                if (onTriggerCallback != null) {
                    onTriggerCallback.accept(event);
                }
                synchronized (state) {
                    state.status = MonitorState.Status.MONITORING;
                    state.startPleasure = oldP;
                    state.peakPleasure = newP;
                    state.startArousal = oldA;
                    state.peakArousal = newA;
                    state.startTime = now;
                    state.lastMsgTime = now;
                }
            } catch (Exception e) {
                log.error("Trigger callback failed, staying IDLE - userId={}", userId, e);
            }
        };
    }

    private Runnable prepareEndEvent(String userId, MonitorState state, double endP, double endA, String endReason) {
        LocalDateTime endTime = LocalDateTime.now();
        boolean isPositiveEnd = (endP - state.startPleasure) >= 0;
        AnchorEvent.EndType endType = isPositiveEnd
                ? AnchorEvent.EndType.POSITIVE
                : AnchorEvent.EndType.NEGATIVE;

        LocalDateTime capturedStartTime = state.startTime;
        double capturedStartPleasure = state.startPleasure;
        double capturedPeakPleasure = state.peakPleasure;
        double capturedStartArousal = state.startArousal;
        double capturedPeakArousal = state.peakArousal;

        log.info("锚点事件结束 - userId={}, endType={}, endReason={}, duration={}s",
                userId, endType, endReason,
                Duration.between(capturedStartTime, endTime).getSeconds());

        AnchorEvent event = AnchorEvent.builder()
                .userId(userId)
                .startTime(capturedStartTime)
                .endTime(endTime)
                .startPleasure(toBigDecimal(capturedStartPleasure))
                .peakPleasure(toBigDecimal(capturedPeakPleasure))
                .endPleasure(toBigDecimal(endP))
                .deltaPleasure(toBigDecimal(capturedPeakPleasure - capturedStartPleasure))
                .startArousal(toBigDecimal(capturedStartArousal))
                .peakArousal(toBigDecimal(capturedPeakArousal))
                .endArousal(toBigDecimal(endA))
                .deltaArousal(toBigDecimal(endA - capturedStartArousal))
                .endType(endType)
                .endReason(endReason)
                .triggerReason("愉悦度变化 " + String.format("%.4f", Math.abs(capturedPeakPleasure - capturedStartPleasure)) + " 超过阈值 " + TRIGGER_THRESHOLD)
                .build();
        event.calculateDuration();

        return () -> {
            try {
                if (onEndCallback != null) {
                    onEndCallback.accept(event);
                }
                synchronized (state) {
                    state.status = MonitorState.Status.IDLE;
                }
            } catch (Exception e) {
                log.error("End callback failed, reverting to MONITORING - userId={}", userId, e);
                synchronized (state) {
                    state.status = MonitorState.Status.MONITORING;
                }
            }
        };
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

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
