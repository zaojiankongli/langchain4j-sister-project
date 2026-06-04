package com.zjkl.recommendation.scheduler;

import com.zjkl.recommendation.mapper.UserRecommendationMapper;
import com.zjkl.recommendation.util.RecommendationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 推荐数据清理定时任务
 * 每天凌晨 3:00 清理 30 天前的旧推荐数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationCleanupScheduler {

    private final UserRecommendationMapper recommendationMapper;

    /**
     * 每天凌晨 3:00 执行清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldRecommendations() {
        log.info("========== 开始清理过期推荐数据 ==========");

        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(RecommendationConstants.CLEANUP_RETENTION_DAYS);
            int deleted = recommendationMapper.deleteOlderThan(cutoffDate);
            log.info("清理完成: 删除了 {} 条 {} 天前的推荐数据", deleted, RecommendationConstants.CLEANUP_RETENTION_DAYS);
        } catch (Exception e) {
            log.error("清理过期推荐数据失败", e);
        }

        log.info("========== 过期推荐数据清理结束 ==========");
    }
}
