package com.zjkl.mail.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 智能信件调度器
 * <p>
 * 利用现有情绪引擎和活跃度数据，自动生成个性化信件。
 * 信件内容随用户状态变化，让系统有"活着"的感觉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailScheduler {

    private static final String DAILY_SUMMARY_COOLDOWN_KEY_PREFIX = "user:mail:daily-summary:";
    private static final String SILENCE_MAIL_COOLDOWN_KEY_PREFIX = "user:mail:silence-cooldown:";

    private final MailService mailService;
    private final EmotionService emotionService;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;

    /** 沉默阈值：超过此小时数未互动则发送关怀信件 */
    private static final long SILENT_HOURS_THRESHOLD = 48;
    private static final Duration SILENCE_MAIL_COOLDOWN = Duration.ofDays(1);

    /**
     * 每日情绪总结 — 每天早上 10:00 执行
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void dailyEmotionSummary() {
        Set<String> userIds = userActivityTracker.getActiveMemoryIdsInLastDays(3);
        if (userIds.isEmpty()) {
            log.debug("每日情绪总结：无活跃用户");
            return;
        }

        // 限制处理用户数，防止定时任务执行过久
        List<String> limitedUserIds = userIds.stream().limit(200).toList();

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd"));
        int sent = 0;

        for (String userId : limitedUserIds) {
            String cooldownKey = DAILY_SUMMARY_COOLDOWN_KEY_PREFIX + LocalDate.now() + ":" + userId;
            boolean acquired = false;
            try {
                Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                        cooldownKey,
                        "1",
                        Duration.ofDays(1)
                );
                acquired = Boolean.TRUE.equals(setIfAbsent);
                if (!acquired) continue;

                EmotionalState emotion = emotionService.getUserEmotion(userId);
                String moodLabel = emotionService.getUserMoodLabel(userId);
                String subject = "情绪小结 · " + today;
                String excerpt = buildDailySummary(moodLabel, emotion);

                mailService.addMail(userId, "EMOTION", subject, excerpt);
                sent++;
            } catch (Exception e) {
                if (acquired) {
                    redisTemplate.delete(cooldownKey);
                }
                log.warn("每日情绪总结失败: userId={}", userId, e);
            }
        }

        log.info("每日情绪总结完成：活跃用户={}, 处理用户={}, 发送={} 封", userIds.size(), limitedUserIds.size(), sent);
    }

    /**
     * 沉默关怀检查 — 每 2 小时执行一次
     */
    @Scheduled(cron = "0 0 */2 * * ?")
    public void inactivityCheck() {
        Set<String> userIds = userActivityTracker.getActiveMemoryIdsInLastDays(7);
        if (userIds.isEmpty()) return;

        int sent = 0;
        for (String userId : userIds) {
            String cooldownKey = SILENCE_MAIL_COOLDOWN_KEY_PREFIX + userId;
            boolean acquired = false;
            try {
                Long lastActive = userActivityTracker.getLastActiveTime(userId);
                if (lastActive == null) continue;

                long silentHours = (System.currentTimeMillis() - lastActive) / 3600000;
                if (silentHours < SILENT_HOURS_THRESHOLD) continue;
                Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                        cooldownKey,
                        "1",
                        SILENCE_MAIL_COOLDOWN
                );
                acquired = Boolean.TRUE.equals(setIfAbsent);
                if (!acquired) continue;

                String subject = "一天又一天……";
                String excerpt = silenceMessage(silentHours);
                mailService.addMail(userId, "NOTICE", subject, excerpt);
                sent++;
            } catch (Exception e) {
                if (acquired) {
                    redisTemplate.delete(cooldownKey);
                }
                log.warn("沉默关怀检查失败: userId={}", userId, e);
            }
        }

        if (sent > 0) {
            log.info("沉默关怀完成：发送={} 封", sent);
        }
    }

    // ==================== 邮件内容生成 ====================

    private String buildDailySummary(String moodLabel, EmotionalState emotion) {
        double p = emotion.getPleasure();
        double a = emotion.getArousal();
        double d = emotion.getDominance();

        if (p > 0.3) {
            return "今天心情不错呢～愉悦度 " + String.format("%.2f", p) +
                    "，整体感觉" + (moodLabel != null ? moodLabel : "挺好的") +
                    "。继续保持这个状态！";
        } else if (p < -0.3) {
            return "感觉你今天有些" + (moodLabel != null ? moodLabel : "低落") +
                    "，愉悦度 " + String.format("%.2f", p) +
                    "。我一直在，想聊聊随时找我。";
        } else {
            String arousal = a > 0.5 ? "平静中带着一点活跃" : "比较平静放松";
            String dominance = d > 0.3 ? "，掌控感不错" : "";
            return "今天情绪" + arousal + dominance +
                    "。简简单单的一天也挺好～";
        }
    }

    private String silenceMessage(long silentHours) {
        long days = silentHours / 24;
        if (days <= 2) {
            return "嘿，最近在忙什么？虽然只有两天没见，感觉过了好久……";
        } else if (days <= 5) {
            return "已经 " + days + " 天没见到你了。不知道你最近过得怎么样？有点想你。";
        } else {
            return "好久不见呀……已经 " + days + " 天没有你的消息了。我一直在这里，随时等你回来。";
        }
    }
}
