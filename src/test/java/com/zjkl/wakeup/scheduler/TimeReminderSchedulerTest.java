package com.zjkl.wakeup.scheduler;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeReminderSchedulerTest {

    @Mock
    private ChatPushService chatPushService;

    @Mock
    private UserActivityTracker userActivityTracker;

    @Mock
    private WakeUpTracker wakeUpTracker;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TimeReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TimeReminderScheduler(chatPushService, userActivityTracker, wakeUpTracker, redisTemplate);
    }

    @Test
    void checkTimeRemindersAtHour_shouldSkipWhenDistributedReminderCooldownAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1)).thenReturn(Set.of("u1"));
        when(chatPushService.isUserConnected("u1")).thenReturn(true);
        when(valueOperations.setIfAbsent(
                eq("time-reminder:" + LocalDate.now() + ":12:u1"),
                eq("1"),
                eq(Duration.ofHours(2))
        )).thenReturn(false);

        scheduler.checkTimeRemindersAtHour(12);

        verify(chatPushService, never()).pushSystem("u1", "到午饭时间啦，工作了一上午，该补充能量了～");
    }

    @Test
    void checkTimeRemindersAtHour_shouldReleaseReminderCooldownWhenPushFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1)).thenReturn(Set.of("u1"));
        when(chatPushService.isUserConnected("u1")).thenReturn(true);
        when(valueOperations.setIfAbsent(
                eq("time-reminder:" + LocalDate.now() + ":12:u1"),
                eq("1"),
                eq(Duration.ofHours(2))
        )).thenReturn(true);
        doThrow(new RuntimeException("push failed"))
                .when(chatPushService)
                .pushSystem("u1", "到午饭时间啦，工作了一上午，该补充能量了～");

        scheduler.checkTimeRemindersAtHour(12);

        verify(redisTemplate).delete("time-reminder:" + LocalDate.now() + ":12:u1");
    }
}
