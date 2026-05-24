package com.zjkl.user.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.user.service.InterestTagGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 用户兴趣标签生成定时任务
 * 每天凌晨 2:00 执行标签生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestTagGenerateScheduler {

    private final InterestTagGenerateService interestTagGenerateService;
    private final UserActivityTracker userActivityTracker;

    /**
     * 每天凌晨 2:00 执行标签生成
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 ? * *")
    public void generateDayInterestTags() {
        log.info("========== 开始每天AI给用户标签生成任务 ==========");

        try {
            // 获取近 1 天有活动的用户
            Set<String> activeUserIds = userActivityTracker.getActiveMemoryIdsInLastDays(1);
            int successCount = 0;

            for (String userId : activeUserIds) {
                try {
                    var tags = interestTagGenerateService.generateTags(userId);
                    if (!tags.isEmpty()) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("为用户 {} 生成兴趣标签失败", userId, e);
                }
            }

            log.info("兴趣标签生成任务执行完成: 共 {} 个活跃用户, 成功生成 {} 个用户标签",
                    activeUserIds.size(), successCount);
            log.info("========== 每天兴趣标签生成任务结束 ==========");

        } catch (Exception e) {
            log.error("每天兴趣标签生成任务执行失败", e);
        }
    }
}