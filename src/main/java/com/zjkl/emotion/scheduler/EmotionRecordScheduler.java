package com.zjkl.emotion.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.service.EmotionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final EmotionRecordService emotionRecordService;
    private final UserActivityTracker userActivityTracker;

    @Scheduled(cron = "0 0 8,12,16,20 * * ?")
    public void recordUserEmotions() {
        log.info("========== 定时情绪记录任务开始 ==========");

        try {
            Set<String> activeUserIds = userActivityTracker.getActiveMemoryIdsInLastDays(1);
            int successCount = 0;

            for (String userId : activeUserIds) {
                try {
                    emotionRecordService.recordEmotionAsync(userId);
                    successCount++;
                } catch (Exception e) {
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
