package com.zjkl.wakeup.scheduler;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 定时提醒调度器
 *
 * 在关键时间点向在线用户推送提醒消息（文本 + Live2D 气泡）
 * 与 WakeUpScheduler 的"主动唤醒"不同，这里只做固定时间提醒，不做 LLM 生成。
 *
 * 触发时间：
 *  早餐 07:00   — "早上好～早餐是一天能量的来源，记得吃哦！"
 *  午餐 12:00   — "到午饭时间啦，工作了一上午，该补充能量了～"
 *  晚餐 18:00   — "晚饭时间到了，今天辛苦啦～"
 *  深夜 23:00   — "已经很晚了，早点休息吧～"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeReminderScheduler {

    private static final String TIME_REMINDER_KEY_PREFIX = "time-reminder:";

    private final ChatPushService chatPushService;
    private final UserActivityTracker userActivityTracker;
    private final WakeUpTracker wakeUpTracker;
    private final StringRedisTemplate redisTemplate;

    /** 提醒消息配置：{hour, message} */
    private static final List<TimeReminder> REMINDERS = List.of(
        new TimeReminder(7,  "早上好～早餐是一天能量的来源，记得吃哦！"),
        new TimeReminder(12, "到午饭时间啦，工作了一上午，该补充能量了～"),
        new TimeReminder(18, "晚饭时间到了，今天辛苦啦～"),
        new TimeReminder(23, "已经很晚了，早点休息吧～")
    );

    /** 每小时执行一次，检查是否需要发送提醒 */
    @Scheduled(cron = "0 0 7,12,18,23 * * ?")
    public void checkTimeReminders() {
        checkTimeRemindersAtHour(LocalTime.now().getHour());
    }

    void checkTimeRemindersAtHour(int currentHour) {

        // 查找当前小时对应的提醒
        TimeReminder reminder = REMINDERS.stream()
                .filter(r -> r.hour() == currentHour)
                .findFirst()
                .orElse(null);

        if (reminder == null) return;

        log.info("定时提醒触发：hour={}, message={}", currentHour, reminder.message());

        // 只推送给当前在线的用户
        Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(1);
        int sentCount = 0;

        for (String userId : activeUsers) {
            String dedupKey = TIME_REMINDER_KEY_PREFIX + LocalDate.now() + ":" + currentHour + ":" + userId;
            boolean acquired = false;
            try {
                // 只推送给 WebSocket 在线的用户
                if (!chatPushService.isUserConnected(userId)) {
                    continue;
                }

                // 深夜提醒 (23点) 需检查冷却，其他时段直接发送
                if (currentHour == 23) {
                    Integer minutesSinceWakeup = wakeUpTracker.getMinutesSinceLastWakeup(userId);
                    if (minutesSinceWakeup != null && minutesSinceWakeup < 30) {
                        log.debug("用户刚互动过，跳过深夜提醒：userId={}", userId);
                        continue;
                    }
                }

                Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                        dedupKey,
                        "1",
                        Duration.ofHours(2)
                );
                acquired = Boolean.TRUE.equals(setIfAbsent);
                if (!acquired) {
                    continue;
                }

                // 通过 SYSTEM 消息推送（Live2D 气泡 + 聊天框显示）
                chatPushService.pushSystem(userId, reminder.message());
                sentCount++;
                log.debug("定时提醒已发送：userId={}, hour={}", userId, currentHour);

            } catch (Exception e) {
                if (acquired) {
                    redisTemplate.delete(dedupKey);
                }
                log.warn("定时提醒发送失败：userId={}", userId, e);
            }
        }

        log.info("定时提醒完成：hour={}, sentCount={}", currentHour, sentCount);
    }

    private record TimeReminder(int hour, String message) {}
}
