package com.zjkl.ai.chat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.common.config.properties.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesktopOmniRealtimeSessionTest {

    @Mock
    private AiProperties aiProperties;

    @Mock
    private ChatPushService chatPushService;

    @Mock
    private ConverMessageService converMessageService;

    @Mock
    private Executor asyncExecutor;

    @Mock
    private WebSocket webSocket;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PetRealtimeStartRequest startRequest;
    private DesktopOmniRealtimeSession session;

    @BeforeEach
    void setUp() {
        startRequest = new PetRealtimeStartRequest();
        startRequest.setVoice("Ethan");
        startRequest.setThreshold(0.5);
        startRequest.setSilenceDurationMs(800);

        when(aiProperties.getRealtimeUrl()).thenReturn("wss://example.com/realtime");
        when(aiProperties.getRealtimeModelName()).thenReturn("qwen-omni-test");
        when(aiProperties.getChatApiKey()).thenReturn("test-api-key");
        when(aiProperties.getRealtimeVoice()).thenReturn("Ethan");

        when(webSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        session = new DesktopOmniRealtimeSession(
                "u1",
                startRequest,
                aiProperties,
                chatPushService,
                converMessageService,
                objectMapper,
                null,
                asyncExecutor,
                () -> {
                }
        );
    }

    @Test
    void onOpen_shouldClampInvalidSettingsInSessionUpdatePayload() {
        startRequest.setVoice("bad voice!");
        startRequest.setThreshold(9.9);
        startRequest.setSilenceDurationMs(10);

        session.onOpen(webSocket);

        ArgumentCaptor<CharSequence> payloadCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(webSocket).sendText(payloadCaptor.capture(), eq(true));

        String payload = payloadCaptor.getValue().toString();
        assertTrue(payload.contains("\"type\":\"session.update\""));
        assertTrue(payload.contains("\"voice\":\"Ethan\""));
        assertTrue(payload.contains("\"threshold\":0.9"));
        assertTrue(payload.contains("\"silence_duration_ms\":300"));
    }

    @Test
    void sessionUpdated_shouldFlushBufferedAudioChunk() {
        session.appendAudio("AQID");

        session.onOpen(webSocket);
        session.onText(webSocket, "{\"type\":\"session.updated\"}", true);

        ArgumentCaptor<CharSequence> payloadCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(webSocket, org.mockito.Mockito.atLeast(2)).sendText(payloadCaptor.capture(), eq(true));

        List<String> payloads = payloadCaptor.getAllValues().stream().map(Object::toString).toList();
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"session.update\"")));
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"input_audio_buffer.append\"")));
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"audio\":\"AQID\"")));
        verify(chatPushService).pushSystem("u1", "实时语音已就绪");
    }

    @Test
    void errorEvent_shouldPushErrorAndCloseSession() {
        session.onOpen(webSocket);
        session.onText(webSocket, "{\"type\":\"error\",\"error\":{\"message\":\"boom\"}}", true);

        verify(chatPushService).pushError("u1", "boom");
        verify(chatPushService).pushPetMotion("u1", "idle", "normal");
        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "realtime error");
    }
}
