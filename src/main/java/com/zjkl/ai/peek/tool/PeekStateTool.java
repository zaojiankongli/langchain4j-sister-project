package com.zjkl.ai.peek.tool;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.common.config.properties.PeekProperties;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Peek 专用状态工具
 *
 * 负责：概率计算、cooldown 管理、peek 开关、活跃时长计算
 * 复用 UserStateTool 的 DND 检测和 wakeup 互斥检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeekStateTool {

    private final StringRedisTemplate redisTemplate;
    private final UserStateTool userStateTool;
    private final UserActivityTracker userActivityTracker;

    // Redis Key
    private static final String LAST_PEEK_KEY_PREFIX = "user:last_peek:";
    private static final String PEEK_ENABLED_KEY_PREFIX = "user:peek:enabled:";
    private static final Long PEEK_EXPIRE_DAYS = 7L;

    // 概率参数
    private final PeekProperties peekProperties;

    private static final int NEVER_PEEKED_MINUTES = Integer.MAX_VALUE;

    // ========== 概率计算 ==========

    /**
     * 计算 peek 概率（固定基础 + 修正因子）
     *
     * @param userId      用户 ID
     * @param timeContext 时间上下文
     * @return peek 概率 0.0 ~ maxProbability
     */
    public double calculatePeekProbability(String userId, TimeContextTool.TimeContext timeContext) {
        double probability = peekProperties.getBaseProbability();
        double modifier = 1.0;

        // 饭点：提醒吃饭很自然
        String moment = timeContext.specialMoment();
        if ("午餐时间".equals(moment) || "晚餐时间".equals(moment)) {
            modifier *= 1.4;
        } else if ("早餐时间".equals(moment)) {
            modifier *= 1.2;
        }

        // 深夜：不打扰
        if (timeContext.currentTime().compareTo("22:00") >= 0) {
            modifier *= 0.3;
        }

        // 清晨：刚起床别吓人
        if ("清晨".equals(timeContext.timeOfDay())) {
            modifier *= 0.4;
        }

        // 周末：更放松
        if (timeContext.isWeekend()) {
            modifier *= 1.1;
        }

        // 下午时段：可能已工作较久
        if ("下午".equals(timeContext.timeOfDay())) {
            modifier *= 1.2;
        }

        // 持续活跃加成
        int continuousMinutes = getContinuousActiveMinutes(userId);
        if (continuousMinutes >= 120) {
            modifier *= 1.5;
        } else if (continuousMinutes >= 60) {
            modifier *= 1.3;
        }

        double result = Math.min(probability * modifier, peekProperties.getMaxProbability());

        log.debug("peek 概率：userId={}, activeMinutes={}, base={}, modifier={}, result={}",
                userId, continuousMinutes, String.format("%.3f", probability),
                String.format("%.2f", modifier), String.format("%.3f", result));

        return result;
    }

    // ========== 快速过滤方法 ==========

    /**
     * 检查用户是否开启了 peek
     */
    public boolean isPeekEnabled(String userId) {
        if (userId == null || userId.contains(":") || userId.isBlank()) {
            return false;
        }
        String value = redisTemplate.opsForValue().get(PEEK_ENABLED_KEY_PREFIX + userId);
        return "true".equals(value);
    }

    /**
     * 检查用户是否在活跃状态（最近 N 分钟内有操作）
     */
    public boolean isUserActive(String userId) {
        Long lastActive = userActivityTracker.getLastActiveTime(userId);
        if (lastActive == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastActive) < peekProperties.getActiveThresholdMinutes() * 60 * 1000L;
    }

    /**
     * 检查 wakeup 互斥（最近 N 分钟内是否发过 wakeup）
     */
    public boolean isWakeupMutex(String userId) {
        Integer minutesSinceLastWakeup = userStateTool.getMinutesSinceLastWakeup(userId);
        return minutesSinceLastWakeup < peekProperties.getWakeupMutexMinutes();
    }

    /**
     * 检查 peek cooldown 是否已过
     */
    public boolean isCooldownPassed(String userId) {
        Integer minutesSinceLastPeek = getMinutesSinceLastPeek(userId);
        return minutesSinceLastPeek >= peekProperties.getCooldownMinutes();
    }

    // ========== 活跃时长计算 ==========

    /**
     * 获取用户连续活跃时长（分钟）
     * 由于 processUserPeek 前置已通过 isUserActive（最近 5 分钟有操作）过滤，
     * 此时用户必定属于连续活跃状态，直接返回距离上次活跃的时长。
     * 注意：未利用历史轨迹回溯，近似值可能略小于真实连续时长。
     */
    public int getContinuousActiveMinutes(String userId) {
        Long lastActive = userActivityTracker.getLastActiveTime(userId);
        if (lastActive == null) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - lastActive) / 60000);
    }

    // ========== 读写方法 ==========

    /**
     * 获取距上次 peek 的分钟数
     */
    public Integer getMinutesSinceLastPeek(String userId) {
        String key = LAST_PEEK_KEY_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return NEVER_PEEKED_MINUTES;
        }
        try {
            long lastPeek = Long.parseLong(value);
            return (int) ((System.currentTimeMillis() - lastPeek) / 60000);
        } catch (NumberFormatException e) {
            return NEVER_PEEKED_MINUTES;
        }
    }

    /**
     * 记录本次 peek 时间
     */
    public void recordPeek(String userId) {
        String key = LAST_PEEK_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), Duration.ofDays(PEEK_EXPIRE_DAYS));
        log.debug("记录 peek 时间：userId={}", userId);
    }

    /**
     * 设置用户 peek 开关
     * 注意：enabled=true 时 TTL 为 7 天，用户需重新 opt-in
     */
    public void setPeekEnabled(String userId, boolean enabled) {
        String key = PEEK_ENABLED_KEY_PREFIX + userId;
        if (enabled) {
            redisTemplate.opsForValue().set(key, "true", Duration.ofDays(PEEK_EXPIRE_DAYS));
        } else {
            redisTemplate.delete(key);
        }
        log.info("设置 peek 开关：userId={}, enabled={}", userId, enabled);
    }
}
