package com.zjkl.emotion.service;

import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.service.SisterChatService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.chat.stomp.SemanticPetEventAdapter;
import com.zjkl.anchor.service.AnchorEventService;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.util.LlmResponseStreamParser;
import com.zjkl.settings.model.UserSettings;
import com.zjkl.settings.service.SettingsService;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatVoiceServiceImplTest {

    @Mock private TtsStreamingService ttsStreamingService;
    @Mock private SettingsService settingsService;
    @Mock private EmotionService emotionService;
    @Mock private AnchorEventService anchorService;
    @Mock private SisterChatService sisterChatService;
    @Mock private ChatPushService chatPushService;
    @Mock private SemanticPetEventAdapter semanticPetEventAdapter;
    @Mock private dev.langchain4j.memory.chat.ChatMemoryProvider redisChatMemoryProvider;
    @Mock private ConverMessageService converMessageService;
    @Mock private LlmResponseStreamParser parser;
    @Mock private ChatMemory chatMemory;

    private ChatVoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        Executor ttsExecutor = Runnable::run;
        service = new ChatVoiceServiceImpl(
                ttsStreamingService,
                settingsService,
                emotionService,
                anchorService,
                sisterChatService,
                chatPushService,
                semanticPetEventAdapter,
                redisChatMemoryProvider,
                converMessageService,
                parser,
                directExecutor,
                ttsExecutor
        );
    }

    @Test
    void chatWithVoice_shouldCompleteAndPersistMemory_whenAudioDisabled() throws Exception {
        String userId = "u1";
        String userInput = "你好";

        SisterChatService.ChatResult chatResult = new SisterChatService.ChatResult(
                Flux.just("你", "好"),
                CompletableFuture.completedFuture("image-desc")
        );

        LlmResponseStreamParser.ParsedResult parsedResult = new LlmResponseStreamParser.ParsedResult(
                Mono.just(new VoiceParams(60, 1.0f, 1.0f, "温和地")),
                Flux.just("你", "好"),
                Mono.just(new DeltaEmotion(0.1, 0.2, 0.3))
        );

        when(sisterChatService.chatWithVoice(userInput, userId, null)).thenReturn(chatResult);
        when(parser.parse(any())).thenReturn(parsedResult);
        when(emotionService.getUserEmotion(userId)).thenReturn(new EmotionalState(0.1, 0.2, 0.3));
        when(emotionService.updateUserEmotion(eq(userId), any(DeltaEmotion.class)))
                .thenReturn(new EmotionalState(0.2, 0.3, 0.4));
        UserSettings settings = new UserSettings();
        settings.setTtsEnabled(false);
        when(redisChatMemoryProvider.get(userId)).thenReturn(chatMemory);

        CompletableFuture<Void> future = service.chatWithVoice(userId, userInput, false, null);

        future.get(2, TimeUnit.SECONDS);

        verify(semanticPetEventAdapter).pushChatPhase(userId, SemanticPetEventAdapter.ChatPhase.THINKING);
        verify(semanticPetEventAdapter).pushChatPhase(userId, SemanticPetEventAdapter.ChatPhase.SPEAKING);
        verify(chatPushService).pushText(userId, "你", false);
        verify(chatPushService).pushText(userId, "好", false);
        verify(chatPushService).pushText(userId, "", true);
        verify(chatPushService).pushEmotionUpdate(eq(userId), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString());
        verify(semanticPetEventAdapter).pushMoodExpression(eq(userId), anyString());
        verify(converMessageService).saveMessage(eq(userId), eq("user"), any());
        verify(converMessageService).saveMessage(eq(userId), eq("assistant"), any());
        verify(ttsStreamingService, never()).initTtsSynthesizer(anyString(), any(), any());
        assertNotNull(future);
    }

    @Test
    void chatWithVoice_shouldSurfaceLlmStreamErrors() {
        String userId = "u2";
        String userInput = "hello";
        SisterChatService.ChatResult chatResult = new SisterChatService.ChatResult(
                Flux.just("ignored"),
                CompletableFuture.completedFuture(null)
        );

        when(sisterChatService.chatWithVoice(userInput, userId, null)).thenReturn(chatResult);
        when(parser.parse(any())).thenReturn(new LlmResponseStreamParser.ParsedResult(
                Mono.just(new VoiceParams(60, 1.0f, 1.0f, "温和地")),
                Flux.error(new RuntimeException("stream failed")),
                Mono.just(new DeltaEmotion(0.0, 0.0, 0.0))
        ));

        CompletableFuture<Void> future = service.chatWithVoice(userId, userInput, false, null);

        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // expected
        }

        verify(chatPushService).pushError(userId, "回复生成失败，请稍后重试");
    }
}
