package com.zjkl.emotion.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.service.EmotionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

/**
 * 定时情绪记录任务
 * 每天 8-23 点，每 4 小时记录一次用户情绪状态
 * 时间点: 8:00, 12:00, 16:00, 20:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionRecordScheduler {

    private static final String EMOTION_RECORD_KEY_PREFIX = "emotion-record:";
    private static final int MAX_ACTIVE_USERS_TO_SCAN = 200;

    private final EmotionRecordService emotionRecordService;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 8,12,16,20 * * ?")
    public void recordUserEmotions() {
        recordUserEmotionsAtHour(java.time.LocalTime.now().getHour());
    }

    void recordUserEmotionsAtHour(int hour) {
        log.info("========== 定时情绪记录任务开始 ==========");

        try {
            Set<String> activeUserIds = userActivityTracker.getActiveMemoryIdsInLastDays(1, MAX_ACTIVE_USERS_TO_SCAN);
            int successCount = 0;

            for (String userId : activeUserIds) {
                String dedupKey = EMOTION_RECORD_KEY_PREFIX + LocalDate.now() + ":" + hour + ":" + userId;
                boolean acquired = false;
                try {
                    Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                            dedupKey,
                            "1",
                            Duration.ofHours(4)
                    );
                    acquired = Boolean.TRUE.equals(setIfAbsent);
                    if (!acquired) {
                        continue;
                    }

                    emotionRecordService.recordEmotionAsync(userId);
                    successCount++;
                } catch (Exception e) {
                    if (acquired) {
                        redisTemplate.delete(dedupKey);
                    }
                    log.error("用户 {} 情绪记录提交失败", userId, e);
                }
            }

            log.info("定时情绪记录任务完成: {} 个活跃用户, 提交 {} 个异步记录",
                    activeUserIds.size(), successCount);
        } catch (Exception e) {
            log.error("定时情绪记录任务执行失败", e);
        }

        log.info("========== 定时情绪记录任务结束 ==========");
    }
}
