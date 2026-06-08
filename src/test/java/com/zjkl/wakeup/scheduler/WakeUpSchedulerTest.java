package com.zjkl.wakeup.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.anchor.service.AnchorEventService;
import com.zjkl.common.config.properties.WakeUpProperties;
import com.zjkl.common.event.WakeUpSentEvent;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.VoiceSynthesisService;
import com.zjkl.settings.model.UserSettings;
import com.zjkl.settings.service.SettingsService;
import com.zjkl.user.service.UserProfileService;
import com.zjkl.wakeup.agent.WakeUpArbiterAgent;
import com.zjkl.wakeup.agent.WakeUpGenerator1Agent;
import com.zjkl.wakeup.agent.WakeUpGenerator2Agent;
import com.zjkl.wakeup.agent.WakeUpGenerator3Agent;
import com.zjkl.wakeup.agent.WakeUpScorer1Agent;
import com.zjkl.wakeup.agent.WakeUpScorer2Agent;
import com.zjkl.wakeup.agent.WakeUpScorer3Agent;
import com.zjkl.wakeup.arbiter.WakeUpArbiter;
import com.zjkl.wakeup.generator.WakeUpContentGenerator;
import com.zjkl.wakeup.scorer.WakeUpScorer;
import com.zjkl.wakeup.template.WakeUpPromptBuilder;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import com.zjkl.wakeup.workflow.WakeUpWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WakeUpSchedulerTest {

    @Mock private UserActivityTracker userActivityTracker;
    @Mock private UserStateTool userStateTool;
    @Mock private TimeContextTool timeContextTool;
    @Mock private EmotionService emotionService;
    @Mock private VoiceSynthesisService voiceSynthesisService;
    @Mock private ChatPushService chatPushService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private WakeUpGenerator1Agent generator1Agent;
    @Mock private WakeUpGenerator2Agent generator2Agent;
    @Mock private WakeUpGenerator3Agent generator3Agent;
    @Mock private WakeUpScorer1Agent scorer1Agent;
    @Mock private WakeUpScorer2Agent scorer2Agent;
    @Mock private WakeUpScorer3Agent scorer3Agent;
    @Mock private WakeUpArbiterAgent arbiterAgent;
    @Mock private WakeUpTracker wakeUpTracker;
    @Mock private UserProfileService userProfileService;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private SettingsService settingsService;

    private WakeUpWorkflow wakeUpWorkflow;

    private WakeUpScheduler scheduler;

    @BeforeEach
    void setUp() {
        WakeUpPromptBuilder promptBuilder = new WakeUpPromptBuilder();
        ObjectMapper objectMapper = new ObjectMapper();
        WakeUpContentGenerator contentGenerator = new WakeUpContentGenerator(objectMapper);
        WakeUpScorer scorer = new WakeUpScorer(objectMapper);
        WakeUpArbiter arbiter = new WakeUpArbiter(objectMapper);
        WakeUpProperties wakeUpProperties = new WakeUpProperties();

        wakeUpWorkflow = new WakeUpWorkflow(
                voiceSynthesisService,
                chatPushService,
                eventPublisher,
                emotionService,
                settingsService,
                userStateTool,
                userProfileService,
                promptTemplateService,
                promptBuilder,
                contentGenerator,
                scorer,
                arbiter,
                wakeUpTracker,
                generator1Agent,
                generator2Agent,
                generator3Agent,
                scorer1Agent,
                scorer2Agent,
                scorer3Agent,
                arbiterAgent
        );

        scheduler = new WakeUpScheduler(
                userActivityTracker,
                timeContextTool,
                redisTemplate,
                wakeUpWorkflow,
                wakeUpProperties
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void processUserWakeUp_shouldNotReturnSentStatusWhenFallbackSendFails() {
        var timeContext = new TimeContextTool.TimeContext("10:00", "上午", "平常日", "1", false, "早上好");
        var state = new UserStateTool.UserStateSnapshot("平静", 0.1, 10.0, 999, false, "上午", "平常日", null, "无历史锚点事件");
        UserSettings settings = new UserSettings();
        settings.setTtsEnabled(false);

        when(valueOperations.setIfAbsent(eq("wakeup:processing:u1"), eq("1"), any())).thenReturn(true);
        when(userStateTool.isDoNotDisturb("u1")).thenReturn(false);
        when(userStateTool.getMinutesSinceLastWakeup("u1")).thenReturn(999);
        when(userStateTool.getSilentHours("u1")).thenReturn(10.0);
        when(userStateTool.calculateWakeProbability(eq("u1"), eq(10.0), eq(timeContext))).thenReturn(1.0);
        when(userStateTool.buildStateSnapshot("u1", timeContext, false, 10.0, 999)).thenReturn(state);
        when(emotionService.getUserEmotion("u1")).thenReturn(new EmotionalState(0.1, 0.2, 0.3));
        when(emotionService.getUserMoodLabel("u1")).thenReturn("平静");
        when(settingsService.getSettings("u1")).thenReturn(settings);
        lenient().when(promptTemplateService.render(anyString(), any())).thenReturn("character-core");
        when(generator1Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(generator2Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(generator3Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        doThrow(new RuntimeException("push failed")).when(chatPushService).pushText(eq("u1"), anyString(), eq(true));

        int result = (int) ReflectionTestUtils.invokeMethod(scheduler, "processUserWakeUp", "u1", timeContext);

        assertEquals(2, result);
        verify(wakeUpTracker, never()).recordSent(anyString(), any(), any(), anyInt(), anyInt(), anyString());
        verify(userStateTool, never()).recordWakeUp("u1");
    }

    @Test
    void processUserWakeUp_shouldStillRecordWakeUpWhenAudioPushFailsAfterTextDelivered() {
        var timeContext = new TimeContextTool.TimeContext("10:00", "上午", "平常日", "1", false, "早上好");
        var state = new UserStateTool.UserStateSnapshot("平静", 0.1, 10.0, 999, false, "上午", "平常日", null, "无历史锚点事件");
        UserSettings settings = new UserSettings();
        settings.setTtsEnabled(true);

        when(valueOperations.setIfAbsent(eq("wakeup:processing:u1"), eq("1"), any())).thenReturn(true);
        when(userStateTool.isDoNotDisturb("u1")).thenReturn(false);
        when(userStateTool.getMinutesSinceLastWakeup("u1")).thenReturn(999);
        when(userStateTool.getSilentHours("u1")).thenReturn(10.0);
        when(userStateTool.calculateWakeProbability(eq("u1"), eq(10.0), eq(timeContext))).thenReturn(1.0);
        when(userStateTool.buildStateSnapshot("u1", timeContext, false, 10.0, 999)).thenReturn(state);
        when(emotionService.getUserEmotion("u1")).thenReturn(new EmotionalState(0.1, 0.2, 0.3));
        when(emotionService.getUserMoodLabel("u1")).thenReturn("平静");
        when(settingsService.getSettings("u1")).thenReturn(settings);
        lenient().when(promptTemplateService.render(anyString(), any())).thenReturn("character-core");
        when(generator1Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(generator2Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(generator3Agent.generate(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(voiceSynthesisService.synthesize(anyString(), any(VoiceSynthesisParam.class))).thenReturn(java.nio.ByteBuffer.wrap(new byte[]{1, 2}));
        doThrow(new RuntimeException("audio failed")).when(chatPushService).pushAudio(eq("u1"), any());

        int result = (int) ReflectionTestUtils.invokeMethod(scheduler, "processUserWakeUp", "u1", timeContext);

        assertEquals(3, result);
        verify(chatPushService).pushText(eq("u1"), anyString(), eq(true));
        verify(userStateTool).recordWakeUp("u1");
        verify(eventPublisher).publishEvent(any(WakeUpSentEvent.class));
    }

    @Test
    void checkUsersForWakeUp_shouldProcessUsersInBatches() {
        var timeContext = new TimeContextTool.TimeContext("10:00", "上午", "平常日", "1", false, "早上好");
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        when(userActivityTracker.getActiveMemoryIdsInLastDays(7, 200)).thenReturn(Set.of("u1", "u2", "u3", "u4", "u5"));
        when(timeContextTool.getCurrentContext()).thenReturn(timeContext);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(userStateTool.isDoNotDisturb(anyString())).thenAnswer(invocation -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(150);
                return true;
            } finally {
                inFlight.decrementAndGet();
            }
        });

        scheduler.checkUsersForWakeUp();

        assertEquals(5, maxInFlight.get() <= 4 ? 5 : -1);
        verify(userStateTool, times(5)).isDoNotDisturb(anyString());
    }
}
