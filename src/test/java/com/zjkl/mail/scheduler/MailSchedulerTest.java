package com.zjkl.mail.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.mail.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSchedulerTest {

    @Mock
    private MailService mailService;

    @Mock
    private EmotionService emotionService;

    @Mock
    private UserActivityTracker userActivityTracker;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MailScheduler mailScheduler;

    @BeforeEach
    void setUp() {
        mailScheduler = new MailScheduler(mailService, emotionService, userActivityTracker, redisTemplate);
    }

    @Test
    void dailyEmotionSummary_shouldSendEmotionSummaryForActiveUser() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(3)).thenReturn(Set.of("u1"));
        when(emotionService.getUserEmotion("u1")).thenReturn(new EmotionalState(0.5, 0.2, 0.4));
        when(emotionService.getUserMoodLabel("u1")).thenReturn("开心");
        when(valueOperations.setIfAbsent(eq("user:mail:daily-summary:" + java.time.LocalDate.now() + ":u1"), eq("1"), eq(Duration.ofDays(1))))
                .thenReturn(true);

        mailScheduler.dailyEmotionSummary();

        verify(mailService).addMail(eq("u1"), eq("EMOTION"), eq("情绪小结 · " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))), anyString());
    }

    @Test
    void dailyEmotionSummary_shouldSkipWhenDistributedDailySummaryCooldownAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(3)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(eq("user:mail:daily-summary:" + java.time.LocalDate.now() + ":u1"), eq("1"), eq(Duration.ofDays(1))))
                .thenReturn(false);

        mailScheduler.dailyEmotionSummary();

        verify(mailService, never()).addMail(eq("u1"), eq("EMOTION"), anyString(), anyString());
    }

    @Test
    void dailyEmotionSummary_shouldReleaseCooldownWhenMailSendFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(3)).thenReturn(Set.of("u1"));
        when(emotionService.getUserEmotion("u1")).thenReturn(new EmotionalState(0.5, 0.2, 0.4));
        when(emotionService.getUserMoodLabel("u1")).thenReturn("开心");
        when(valueOperations.setIfAbsent(eq("user:mail:daily-summary:" + java.time.LocalDate.now() + ":u1"), eq("1"), eq(Duration.ofDays(1))))
                .thenReturn(true);
        doThrow(new RuntimeException("send failed")).when(mailService)
                .addMail(eq("u1"), eq("EMOTION"), anyString(), anyString());

        mailScheduler.dailyEmotionSummary();

        verify(redisTemplate).delete("user:mail:daily-summary:" + java.time.LocalDate.now() + ":u1");
    }

    @Test
    void inactivityCheck_shouldNotSendDuplicateSilenceMailWithinCooldown() {
        long now = System.currentTimeMillis();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(7)).thenReturn(Set.of("u1"));
        when(userActivityTracker.getLastActiveTime("u1")).thenReturn(now - 72L * 3600000);
        when(valueOperations.setIfAbsent("user:mail:silence-cooldown:u1", "1", Duration.ofDays(1)))
                .thenReturn(true, false);

        mailScheduler.inactivityCheck();
        mailScheduler.inactivityCheck();

        verify(mailService, times(1)).addMail("u1", "NOTICE", "一天又一天……", "已经 3 天没见到你了。不知道你最近过得怎么样？有点想你。");
    }

    @Test
    void inactivityCheck_shouldSkipWhenDistributedCooldownAlreadyExists() {
        long now = System.currentTimeMillis();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(7)).thenReturn(Set.of("u1"));
        when(userActivityTracker.getLastActiveTime("u1")).thenReturn(now - 72L * 3600000);
        when(valueOperations.setIfAbsent("user:mail:silence-cooldown:u1", "1", Duration.ofDays(1)))
                .thenReturn(false);

        mailScheduler.inactivityCheck();

        verify(mailService, never()).addMail("u1", "NOTICE", "一天又一天……", "已经 3 天没见到你了。不知道你最近过得怎么样？有点想你。");
    }

    @Test
    void inactivityCheck_shouldReleaseCooldownWhenMailSendFails() {
        long now = System.currentTimeMillis();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(7)).thenReturn(Set.of("u1"));
        when(userActivityTracker.getLastActiveTime("u1")).thenReturn(now - 72L * 3600000);
        when(valueOperations.setIfAbsent("user:mail:silence-cooldown:u1", "1", Duration.ofDays(1)))
                .thenReturn(true);
        doThrow(new RuntimeException("send failed")).when(mailService)
                .addMail("u1", "NOTICE", "一天又一天……", "已经 3 天没见到你了。不知道你最近过得怎么样？有点想你。");

        mailScheduler.inactivityCheck();

        verify(redisTemplate).delete("user:mail:silence-cooldown:u1");
    }
}
