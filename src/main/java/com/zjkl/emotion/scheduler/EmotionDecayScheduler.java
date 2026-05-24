package com.zjkl.emotion.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.service.EmotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 情绪衰减定时任务 — 每30分钟对所有活跃用户执行情绪衰减
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionDecayScheduler {

    private final EmotionService emotionService;
    private final UserActivityTracker userActivityTracker;

    @Scheduled(fixedRate = 1800000)
    public void decayActiveUsers() {
        try {
            Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(1);
            if (activeUsers.isEmpty()) {
                return;
            }
            for (String userId : activeUsers) {
                try {
                    emotionService.decayUserEmotion(userId);
                } catch (Exception e) {
                    log.warn("情绪衰减失败: userId={}", userId, e);
                }
            }
            log.debug("情绪衰减完成: {} 用户", activeUsers.size());
        } catch (Exception e) {
            log.warn("情绪衰减定时任务执行失败", e);
        }
    }
}
