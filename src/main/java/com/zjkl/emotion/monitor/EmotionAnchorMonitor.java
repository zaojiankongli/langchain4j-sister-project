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
import java.util.Map;
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
    private static final int MAX_MONITOR_CAPACITY = 10000;

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

        // Invoke callbacks OUTSIDE synchronized block to avoid DB I/O under lock
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

    /**
     * 准备触发事件：在 synchronized 内构建事件，返回 Runnable 在锁外执行回调。
     * 回调成功后才将状态切换为 MONITORING；失败则保持 IDLE。
     */
    private Runnable prepareTriggerEvent(String userId, MonitorState state, double oldP, double newP, double oldA, double newA) {
        LocalDateTime now = LocalDateTime.now();
        log.info("锚点事件触发 - userId={}, deltaP={}, startP={}, newP={}, startA={}, newA={}",
                userId, newP - oldP, oldP, newP, oldA, newA);

        EmotionAnchorEvent event = EmotionAnchorEvent.builder()
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
                // Callback succeeded — now commit state transition to MONITORING
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
                // state.status remains IDLE — no cleanup needed
            }
        };
    }

    /**
     * 准备结束事件：在 synchronized 内快照状态并构建事件，返回 Runnable 在锁外执行回调。
     * 回调成功后将状态切换为 IDLE；失败则回退为 MONITORING。
     */
    private Runnable prepareEndEvent(String userId, MonitorState state, double endP, double endA, String endReason) {
        LocalDateTime endTime = LocalDateTime.now();
        // 比较结束愉悦度与起始愉悦度的差值来判断正负
        // 差值为零视为正面（情绪至少没有恶化）
        boolean isPositiveEnd = (endP - state.startPleasure) >= 0;
        EmotionAnchorEvent.EndType endType = isPositiveEnd
                ? EmotionAnchorEvent.EndType.POSITIVE
                : EmotionAnchorEvent.EndType.NEGATIVE;

        // Snapshot state while still inside synchronized
        LocalDateTime capturedStartTime = state.startTime;
        double capturedStartPleasure = state.startPleasure;
        double capturedPeakPleasure = state.peakPleasure;
        double capturedStartArousal = state.startArousal;
        double capturedPeakArousal = state.peakArousal;

        log.info("锚点事件结束 - userId={}, endType={}, endReason={}, duration={}s",
                userId, endType, endReason,
                Duration.between(capturedStartTime, endTime).getSeconds());

        EmotionAnchorEvent event = EmotionAnchorEvent.builder()
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
                // Callback succeeded — commit transition to IDLE
                synchronized (state) {
                    state.status = MonitorState.Status.IDLE;
                }
            } catch (Exception e) {
                log.error("End callback failed, reverting to MONITORING - userId={}", userId, e);
                // Revert to MONITORING so the next onEmotionChange can retry
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

    /**
     * 定期清理过期状态 + 最大容量检查
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupIdleMonitors() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(CLEANUP_HOURS);
        int before = monitors.size();

        // 1. 清理 IDLE 且超时的用户（synchronized 保证不会误删正在被 onEmotionChange 重新激活的状态）
        monitors.entrySet().removeIf(entry -> {
            MonitorState state = entry.getValue();
            synchronized (state) {
                return state.status == MonitorState.Status.IDLE
                        && state.lastMsgTime != null
                        && state.lastMsgTime.isBefore(threshold);
            }
        });

        // 2. 最大容量检查：如果 map 超过阈值，按 lastMsgTime 最老的优先清除（排除正在监测中的用户）
        if (monitors.size() > MAX_MONITOR_CAPACITY) {
            int excess = monitors.size() - MAX_MONITOR_CAPACITY;
            monitors.entrySet().stream()
                    .filter(entry -> {
                        var s = entry.getValue();
                        synchronized (s) {
                            return s.status != MonitorState.Status.MONITORING;
                        }
                    })
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getValue().lastMsgTime;
                        LocalDateTime timeB = b.getValue().lastMsgTime;
                        if (timeA == null && timeB == null) return 0;
                        if (timeA == null) return -1;
                        if (timeB == null) return 1;
                        return timeA.compareTo(timeB);
                    })
                    .limit(excess)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(monitors::remove);
            log.info("monitors map 超过最大容量 {}，清除最老的 {} 条记录", MAX_MONITOR_CAPACITY, excess);
        }

        int removed = before - monitors.size();
        if (removed > 0) {
            log.debug("清理过期 Monitor - 清理 {}, 剩余 {}", removed, monitors.size());
        }
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
