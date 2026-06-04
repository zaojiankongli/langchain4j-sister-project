package com.zjkl.ai.peek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.peek.agent.PeekContentAgent;
import com.zjkl.ai.peek.tool.PeekStateTool;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.VoiceSynthesisService;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeekCallbackServiceTest {

    @Mock
    private ImageDescriptionService imageDescriptionService;
    @Mock
    private PeekContentAgent peekContentAgent;
    @Mock
    private EmotionService emotionService;
    @Mock
    private VoiceSynthesisService voiceSynthesisService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ConverMessageService converMessageService;
    @Mock
    private ChatPushService chatPushService;
    @Mock
    private PeekStateTool peekStateTool;
    @Mock
    private TimeContextTool timeContextTool;
    @Mock
    private UserStateTool userStateTool;
    @Spy
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private PeekCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PeekCallbackService(
                imageDescriptionService,
                peekContentAgent,
                emotionService,
                voiceSynthesisService,
                promptTemplateService,
                converMessageService,
                chatPushService,
                peekStateTool,
                timeContextTool,
                userStateTool,
                objectMapper,
                stringRedisTemplate
        );

        when(chatPushService.isUserConnected("u1")).thenReturn(true);
        when(imageDescriptionService.describeForPeek("https://img")).thenReturn("在写代码");
        when(timeContextTool.getCurrentContext()).thenReturn(
                new TimeContextTool.TimeContext("23:30", "深夜", "平常日", "3", false, "夜深了")
        );
        when(emotionService.getUserEmotion("u1")).thenReturn(new EmotionalState(0.1, 0.2, 0.3));
        when(emotionService.getMoodDescription(any(EmotionalState.class))).thenReturn("平静");
        when(emotionService.getUserMoodLabel("u1")).thenReturn("温柔");
        when(promptTemplateService.render(anyString(), any())).thenReturn("character-core");
        when(peekStateTool.getContinuousActiveMinutes("u1")).thenReturn(45);
    }

    @Test
    void handlePeekCallback_blankJsonFallsBackToGenericMessage() {
        when(peekContentAgent.generateMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("{}");
        when(voiceSynthesisService.synthesize(anyString(), any(EmotionalState.class)))
                .thenReturn(ByteBuffer.wrap(new byte[]{1, 2, 3}));

        service.handlePeekCallback("u1", "https://img", "peek-1");

        ArgumentCaptor<EmotionalState> emotionCaptor = ArgumentCaptor.forClass(EmotionalState.class);
        verify(voiceSynthesisService).synthesize(anyString(), emotionCaptor.capture());
        EmotionalState actualEmotion = emotionCaptor.getValue();
        assertNotNull(actualEmotion);
        assertEquals(0.1, actualEmotion.getPleasure());
        assertEquals(0.2, actualEmotion.getArousal());
        assertEquals(0.3, actualEmotion.getDominance());
        verify(chatPushService).pushText("u1", "刚刚看了一下，你正在专注呢，我就不打扰啦～", true);
        verify(chatPushService).pushAudio("u1", new byte[]{1, 2, 3});
        verify(peekStateTool).recordPeek("u1");
        verify(userStateTool).recordWakeUp("u1");
    }

    @Test
    void handlePeekCallback_jsonLikeFallbackTextUsesGenericMessage() {
        when(peekContentAgent.generateMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("{not-json}");
        when(voiceSynthesisService.synthesize(anyString(), any(EmotionalState.class)))
                .thenReturn(ByteBuffer.wrap(new byte[]{9}));

        service.handlePeekCallback("u1", "https://img", "peek-2");

        verify(chatPushService).pushText("u1", "刚刚看了一下，你正在专注呢，我就不打扰啦～", true);
        verify(chatPushService).pushAudio("u1", new byte[]{9});
    }

    @Test
    void handlePeekCallback_validJsonUsesMessageAndVoiceParams() {
        when(peekContentAgent.generateMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("""
                        {"message":"早点休息呀","voiceParams":{"volume":60,"speechRate":0.95,"pitchRate":1.05,"instruction":"温柔提醒"}}
                        """);
        when(voiceSynthesisService.synthesize(anyString(), any(VoiceSynthesisParam.class)))
                .thenReturn(ByteBuffer.wrap(new byte[]{4, 5}));

        service.handlePeekCallback("u1", "https://img", "peek-3");

        ArgumentCaptor<VoiceSynthesisParam> paramCaptor = ArgumentCaptor.forClass(VoiceSynthesisParam.class);
        verify(voiceSynthesisService).synthesize(anyString(), paramCaptor.capture());
        VoiceSynthesisParam actual = paramCaptor.getValue();
        assertEquals(60, actual.getVolume());
        assertEquals(0.95f, actual.getSpeechRate());
        assertEquals(1.05f, actual.getPitchRate());
        assertEquals("温柔提醒", actual.getInstruction());

        ArgumentCaptor<List<MessageContent>> contentCaptor = ArgumentCaptor.forClass(List.class);
        verify(converMessageService).saveMessage(anyString(), anyString(), contentCaptor.capture());
        assertEquals(1, contentCaptor.getValue().size());
        assertEquals("text", contentCaptor.getValue().getFirst().getType());
        assertEquals("早点休息呀", contentCaptor.getValue().getFirst().getText());
        verify(chatPushService).pushText("u1", "早点休息呀", true);
        verify(chatPushService).pushAudio("u1", new byte[]{4, 5});
        verify(chatPushService, never()).pushError(anyString(), anyString());
    }

    @Test
    void handlePeekCallback_shouldStillRecordStateWhenAudioPushFailsAfterTextDelivered() {
        when(peekContentAgent.generateMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("""
                        {"message":"早点休息呀","voiceParams":{"volume":60,"speechRate":0.95,"pitchRate":1.05,"instruction":"温柔提醒"}}
                        """);
        when(voiceSynthesisService.synthesize(anyString(), any(VoiceSynthesisParam.class)))
                .thenReturn(ByteBuffer.wrap(new byte[]{4, 5}));
        doThrow(new RuntimeException("audio failed")).when(chatPushService).pushAudio("u1", new byte[]{4, 5});

        service.handlePeekCallback("u1", "https://img", "peek-4");

        verify(chatPushService).pushText("u1", "早点休息呀", true);
        verify(chatPushService).pushAudio("u1", new byte[]{4, 5});
        verify(peekStateTool).recordPeek("u1");
        verify(userStateTool).recordWakeUp("u1");
        verify(chatPushService, never()).pushError(anyString(), anyString());
    }
}
