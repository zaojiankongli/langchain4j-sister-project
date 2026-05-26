package com.zjkl.wakeup.tool;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.common.config.properties.WakeUpProperties;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.monitor.EmotionAnchorMonitor;
import com.zjkl.emotion.service.EmotionAnchorService;
import com.zjkl.emotion.service.EmotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用户状态工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStateTool {

    private final EmotionService emotionService;
    private final EmotionAnchorMonitor anchorMonitor;
    private final EmotionAnchorService anchorService;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;
    private final WakeUpProperties wakeUpProperties;

    // Redis Key
    private static final String DND_KEY_PREFIX = "user:dnd:";
    private static final String LAST_WAKEUP_KEY_PREFIX = "user:last_wakeup:";
    private static final Long EMOTION_EXPIRE_DAYS = 7L;

    /**
     * 用户状态快照
     */
    public record UserStateSnapshot(
            String moodDescription,
            Double moodScore,
            Double silentHours,
            Integer minutesSinceLastWakeup,
            Boolean isDnd,
            String timeOfDay,
            String specialMoment,
            String activeAnchorContext,
            String recentAnchorSummary
    ) {}

    /** 获取状态快照 */
    public UserStateSnapshot getUserState(String userId) {
        EmotionalState emotion = emotionService.getUserEmotion(userId);
        String moodDesc = emotionService.getUserMoodDescription(userId);

        Double silentHours = getSilentHours(userId);
        Integer minutesSinceLastWakeup = getMinutesSinceLastWakeup(userId);
        boolean isDnd = isDoNotDisturb(userId);

        return new UserStateSnapshot(
                moodDesc,
                emotion.getPleasure(),
                silentHours,
                minutesSinceLastWakeup,
                isDnd,
                null, null,
                anchorMonitor.getAnchorContext(userId),
                anchorService.getRecentAnchorSummary(userId)
        );
    }

    /** 构建状态快照 */
    public UserStateSnapshot buildStateSnapshot(String userId,
                                                TimeContextTool.TimeContext timeContext,
                                                boolean isDnd,
                                                Double silentHours,
                                                Integer minutesSinceLastWakeup) {
        // 只查询快速过滤阶段没有查过的数据
        EmotionalState emotion = emotionService.getUserEmotion(userId);
        String moodDesc = emotionService.getMoodDescription(emotion);
        String activeAnchorContext = anchorMonitor.getAnchorContext(userId);
        String recentAnchorSummary = anchorService.getRecentAnchorSummary(userId);

        return new UserStateSnapshot(
                moodDesc,
                emotion.getPleasure(),
                silentHours,
                minutesSinceLastWakeup,
                isDnd,
                timeContext.timeOfDay(),
                timeContext.specialMoment(),
                activeAnchorContext,
                recentAnchorSummary
        );
    }

    // ========== 唤醒概率计算 ==========

    /**
     * 唤醒概率
     */
    public double calculateWakeProbability(String userId, Double silentHours,
                                           TimeContextTool.TimeContext timeContext) {
        // 基础概率
        double base = 1.0 / (1.0 + Math.exp(-wakeUpProperties.getProbabilitySteepness() * (silentHours - wakeUpProperties.getProbabilityMidpoint())));

        // 上下文修正
        double modifier = 1.0;

        // 周末加成
        if (timeContext.isWeekend()) {
            modifier *= 1.1;
        }

        // 饭点加成
        String moment = timeContext.specialMoment();
        if ("早餐时间".equals(moment) || "午餐时间".equals(moment) || "晚餐时间".equals(moment)) {
            modifier *= 1.15;
        }

        // 清晨衰减
        if ("清晨".equals(timeContext.timeOfDay())) {
            modifier *= 0.8;
        }

        // 深夜衰减
        if (timeContext.currentTime().compareTo("21:30") >= 0) {
            modifier *= 0.7;
        }

        // 负面锚点加成
        String anchorCtx = anchorMonitor.getAnchorContext(userId);
        if (anchorCtx != null && anchorCtx.contains("负面")) {
            modifier *= 1.15;
        }

        double result = Math.min(base * modifier, wakeUpProperties.getProbabilityMax());
        log.debug("唤醒概率：userId={}, silentHours={}, base={}, modifier={}, result={}",
                userId, String.format("%.1f", silentHours), String.format("%.3f", base),
                String.format("%.2f", modifier), String.format("%.3f", result));
        return result;
    }

    // ========== 快速过滤方法 ==========

    /** 检查免打扰 */
    public boolean isDoNotDisturb(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(DND_KEY_PREFIX + userId));
    }

    /** 获取沉默时长 */
    public Double getSilentHours(String userId) {
        Long lastActive = userActivityTracker.getLastActiveTime(userId);
        if (lastActive == null) {
            return 999.0;
        }
        return (System.currentTimeMillis() - lastActive) / 3600000.0;
    }

    /** 获取唤醒间隔 */
    public Integer getMinutesSinceLastWakeup(String userId) {
        String key = LAST_WAKEUP_KEY_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 999;
        }
        try {
            long lastWakeup = Long.parseLong(value);
            return (int) ((System.currentTimeMillis() - lastWakeup) / 60000);
        } catch (NumberFormatException e) {
            return 999;
        }
    }

    // ========== 写入方法 ==========

    /** 记录唤醒 */
    public void recordWakeUp(String userId) {
        String key = LAST_WAKEUP_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, Duration.ofDays(EMOTION_EXPIRE_DAYS));
        log.debug("记录唤醒时间：userId={}", userId);
    }

    /** 设置免打扰 */
    public void setDoNotDisturb(String userId, boolean enabled) {
        String key = DND_KEY_PREFIX + userId;
        if (enabled) {
            redisTemplate.opsForValue().set(key, "1");
            redisTemplate.expire(key, Duration.ofDays(EMOTION_EXPIRE_DAYS));
        } else {
            redisTemplate.delete(key);
        }
        log.debug("设置免打扰：userId={}, enabled={}", userId, enabled);
    }
}
