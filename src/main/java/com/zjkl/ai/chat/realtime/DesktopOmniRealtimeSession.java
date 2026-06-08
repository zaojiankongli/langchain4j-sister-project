package com.zjkl.ai.chat.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.common.config.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个用户的一条 Qwen-Omni-Realtime WebSocket 会话。
 */
@Slf4j
class DesktopOmniRealtimeSession implements WebSocket.Listener {

    private static final String DEFAULT_INSTRUCTIONS = "你是陪伴型桌宠 Zeeva，请用自然、亲近、简洁的中文和用户实时语音聊天。";
    private static final double DEFAULT_THRESHOLD = 0.5;
    private static final double MIN_THRESHOLD = 0.1;
    private static final double MAX_THRESHOLD = 0.9;
    private static final int DEFAULT_SILENCE_DURATION_MS = 800;
    private static final int MIN_SILENCE_DURATION_MS = 300;
    private static final int MAX_SILENCE_DURATION_MS = 3_000;
    private static final int DEFAULT_PREFIX_PADDING_MS = 500;
    private static final int MAX_PENDING_AUDIO_CHUNKS = 50;
    private static final int MAX_OUTBOUND_BACKLOG = 200;

    private final String userId;
    private final PetRealtimeStartRequest startRequest;
    private final AiProperties aiProperties;
    private final ChatPushService chatPushService;
    private final ConverMessageService converMessageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Executor asyncExecutor;
    private final Runnable onClosed;

    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean sessionReady = new AtomicBoolean(false);
    private final AtomicBoolean assistantReplyCompleted = new AtomicBoolean(false);
    private final AtomicInteger outboundBacklog = new AtomicInteger(0);
    private final Object outboundLock = new Object();
    private CompletableFuture<Void> outboundTail = CompletableFuture.completedFuture(null);
    private final StringBuilder inboundBuffer = new StringBuilder();
    private final StringBuilder assistantReply = new StringBuilder();
    private final Deque<String> pendingAudioChunks = new ArrayDeque<>();
    private volatile String latestSavedUserTranscript = "";
    private volatile String latestSavedAssistantReply = "";

    DesktopOmniRealtimeSession(String userId,
                               PetRealtimeStartRequest startRequest,
                               AiProperties aiProperties,
                               ChatPushService chatPushService,
                               ConverMessageService converMessageService,
                               ObjectMapper objectMapper,
                               HttpClient httpClient,
                               Executor asyncExecutor,
                               Runnable onClosed) {
        this.userId = userId;
        this.startRequest = startRequest;
        this.aiProperties = aiProperties;
        this.chatPushService = chatPushService;
        this.converMessageService = converMessageService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.asyncExecutor = asyncExecutor;
        this.onClosed = onClosed;
    }

    void connect() {
        URI uri = URI.create(aiProperties.getRealtimeUrl()
                + "?model=" + URLEncoder.encode(aiProperties.getRealtimeModelName(), StandardCharsets.UTF_8));
        httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + aiProperties.getChatApiKey())
                .buildAsync(uri, this)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        log.error("Qwen Realtime 连接失败：userId={}", userId, error);
                        chatPushService.pushError(userId, "实时语音连接失败");
                        chatPushService.pushPetMotion(userId, "idle", "normal");
                        onClosed.run();
                        return;
                    }
                    webSocketRef.set(socket);
                });
    }

    void appendAudio(String audioBase64) {
        if (closed.get()) {
            return;
        }
        WebSocket socket = webSocketRef.get();
        if (socket == null || !open.get() || !sessionReady.get()) {
            enqueuePendingAudio(audioBase64);
            return;
        }
        sendAudioAppend(audioBase64);
    }

    private void enqueuePendingAudio(String audioBase64) {
        synchronized (pendingAudioChunks) {
            if (pendingAudioChunks.size() >= MAX_PENDING_AUDIO_CHUNKS) {
                pendingAudioChunks.pollFirst();
            }
            pendingAudioChunks.addLast(audioBase64);
        }
    }

    private void flushPendingAudio() {
        while (true) {
            String chunk;
            synchronized (pendingAudioChunks) {
                chunk = pendingAudioChunks.pollFirst();
            }
            if (chunk == null || closed.get() || !open.get() || !sessionReady.get()) {
                return;
            }
            sendAudioAppend(chunk);
        }
    }

    private void sendAudioAppend(String audioBase64) {
        send(Map.of(
                "event_id", newEventId(),
                "type", "input_audio_buffer.append",
                "audio", audioBase64
        ));
    }

    void close(String reason) {
        closed.set(true);
        open.set(false);
        sessionReady.set(false);
        synchronized (pendingAudioChunks) {
            pendingAudioChunks.clear();
        }
        WebSocket socket = webSocketRef.getAndSet(null);
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, reason)
                    .exceptionally(error -> {
                        log.debug("Qwen Realtime close 帧发送失败：userId={}", userId, error);
                        return null;
                    });
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocketRef.set(webSocket);
        open.set(true);
        webSocket.request(1);
        sendSessionUpdate();
        chatPushService.pushSystem(userId, "实时语音连接中");
        chatPushService.pushPetMotion(userId, "listening", "normal");
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        inboundBuffer.append(data);
        if (last) {
            String message = inboundBuffer.toString();
            inboundBuffer.setLength(0);
            handleMessage(message);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        open.set(false);
        sessionReady.set(false);
        onClosed.run();
        log.info("Qwen Realtime 已关闭：userId={}, code={}, reason={}", userId, statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        sessionReady.set(false);
        onClosed.run();
        log.error("Qwen Realtime 异常：userId={}", userId, error);
        chatPushService.pushError(userId, "实时语音异常，请重新开启");
        chatPushService.pushPetMotion(userId, "idle", "normal");
    }

    private void sendSessionUpdate() {
        String voice = sanitizeVoice(startRequest.getVoice());
        double threshold = clamp(startRequest.getThreshold(), MIN_THRESHOLD, MAX_THRESHOLD, DEFAULT_THRESHOLD);
        int silenceMs = clamp(startRequest.getSilenceDurationMs(), MIN_SILENCE_DURATION_MS, MAX_SILENCE_DURATION_MS, DEFAULT_SILENCE_DURATION_MS);

        send(Map.of(
                "event_id", newEventId(),
                "type", "session.update",
                "session", Map.of(
                        "modalities", List.of("text", "audio"),
                        "voice", voice,
                        "input_audio_format", "pcm",
                        "output_audio_format", "pcm",
                        "instructions", DEFAULT_INSTRUCTIONS,
                        "input_audio_transcription", Map.of("model", "qwen3-asr-flash-realtime"),
                        "turn_detection", Map.of(
                                "type", "semantic_vad",
                                "threshold", threshold,
                                "prefix_padding_ms", DEFAULT_PREFIX_PADDING_MS,
                                "create_response", true,
                                "interrupt_response", true,
                                "silence_duration_ms", silenceMs
                        )
                )
        ));
    }

    private void handleMessage(String raw) {
        try {
            JsonNode event = objectMapper.readTree(raw);
            String type = OmniRealtimeEventMapper.eventType(event);
            switch (type) {
                case "session.created" -> log.debug("Qwen Realtime 会话已创建：userId={}", userId);
                case "session.updated" -> {
                    sessionReady.set(true);
                    flushPendingAudio();
                    chatPushService.pushSystem(userId, "实时语音已就绪");
                    log.debug("Qwen Realtime 会话配置已生效：userId={}", userId);
                }
                case "input_audio_buffer.speech_started" -> {
                    assistantReply.setLength(0);
                    assistantReplyCompleted.set(false);
                    chatPushService.pushPetMotion(userId, "listening", "normal");
                }
                case "input_audio_buffer.speech_stopped" -> chatPushService.pushPetMotion(userId, "thinking", "normal");
                case "conversation.item.input_audio_transcription.completed" -> handleUserTranscript(event);
                case "conversation.item.input_audio_transcription.failed" -> handleTranscriptionFailed(event);
                case "response.created" -> {
                    assistantReply.setLength(0);
                    assistantReplyCompleted.set(false);
                    chatPushService.pushPetMotion(userId, "speaking", "normal");
                }
                case "response.audio_transcript.delta", "response.text.delta" -> handleAssistantDelta(event);
                case "response.audio_transcript.done", "response.text.done" -> handleAssistantDone(event);
                case "response.content_part.done" -> handleContentPartDone(event);
                case "response.output_item.done" -> handleOutputItemDone(event);
                case "response.audio.delta" -> handleAudioDelta(event);
                case "response.audio.done" -> log.debug("Qwen Realtime 音频完成：userId={}", userId);
                case "response.done" -> handleResponseDone(event);
                case "error" -> handleRealtimeError(event);
                default -> log.debug("未处理 Qwen Realtime 事件：userId={}, type={}", userId, type);
            }
        } catch (Exception e) {
            log.warn("解析 Qwen Realtime 事件失败：userId={}, raw={}", userId, raw, e);
        }
    }

    private void handleTranscriptionFailed(JsonNode event) {
        String message = OmniRealtimeEventMapper.errorMessage(event, "语音识别失败");
        log.warn("Qwen Realtime 转录失败：userId={}, message={}", userId, message);
        chatPushService.pushError(userId, message);
        chatPushService.pushPetMotion(userId, "idle", "normal");
    }

    private void handleUserTranscript(JsonNode event) {
        String transcript = event.path("transcript").asText("").trim();
        if (!hasText(transcript)) {
            return;
        }
        if (transcript.equals(latestSavedUserTranscript)) {
            return;
        }
        latestSavedUserTranscript = transcript;
        chatPushService.pushUserTranscript(userId, transcript);
        CompletableFutureSupport.runAsync(() -> converMessageService.saveMessage(
                userId,
                "user",
                List.of(MessageContent.text(transcript))
        ), asyncExecutor);
    }

    private void handleAssistantDelta(JsonNode event) {
        if (assistantReplyCompleted.get()) {
            return;
        }
        String delta = event.path("delta").asText("");
        if (!delta.isEmpty()) {
            assistantReply.append(delta);
            chatPushService.pushText(userId, delta, false);
            chatPushService.pushPetMotion(userId, "speaking", "normal");
        }
    }

    private void handleAssistantDone(JsonNode event) {
        String transcript = event.path("transcript").asText("");
        if (!hasText(transcript)) {
            transcript = event.path("text").asText(assistantReply.toString());
        }
        completeAssistantReply(transcript);
    }

    private void handleContentPartDone(JsonNode event) {
        String text = event.path("part").path("text").asText("");
        if (hasText(text)) {
            completeAssistantReply(text);
        }
    }

    private void handleOutputItemDone(JsonNode event) {
        String text = OmniRealtimeEventMapper.assistantText(event.path("item"));
        if (hasText(text)) {
            completeAssistantReply(text);
        }
    }

    private void handleResponseDone(JsonNode event) {
        String text = OmniRealtimeEventMapper.assistantText(event.path("response"));
        if (hasText(text) || assistantReply.length() > 0) {
            completeAssistantReply(hasText(text) ? text : assistantReply.toString());
        }
        chatPushService.pushPetMotion(userId, "idle", "normal");
    }

    private void completeAssistantReply(String transcript) {
        if (!assistantReplyCompleted.compareAndSet(false, true)) {
            return;
        }
        String fullReply = transcript != null ? transcript.trim() : "";
        chatPushService.pushText(userId, "", true);
        if (hasText(fullReply) && !fullReply.equals(latestSavedAssistantReply)) {
            latestSavedAssistantReply = fullReply;
            String replyToSave = fullReply;
            CompletableFutureSupport.runAsync(() -> converMessageService.saveMessage(
                    userId,
                    "assistant",
                    List.of(MessageContent.text(replyToSave))
            ), asyncExecutor);
        }
        assistantReply.setLength(0);
    }

    private void handleAudioDelta(JsonNode event) {
        String delta = event.path("delta").asText("");
        if (!delta.isEmpty()) {
            try {
                chatPushService.pushAudio(userId, Base64.getDecoder().decode(delta));
            } catch (IllegalArgumentException e) {
                log.warn("Qwen Realtime 音频分片 Base64 无效：userId={}", userId, e);
            }
        }
    }

    private void handleRealtimeError(JsonNode event) {
        String message = OmniRealtimeEventMapper.errorMessage(event, "实时语音服务错误");
        chatPushService.pushError(userId, message);
        chatPushService.pushPetMotion(userId, "idle", "normal");
        close("realtime error");
    }

    private void send(Map<String, Object> event) {
        try {
            enqueueSerializedSend(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("序列化 Qwen Realtime 事件失败：userId={}", userId, e);
        }
    }

    private void enqueueSerializedSend(String payload) {
        int queued = outboundBacklog.incrementAndGet();
        if (queued > MAX_OUTBOUND_BACKLOG) {
            outboundBacklog.decrementAndGet();
            if (!closed.get()) {
                log.warn("Qwen Realtime 发送队列过载：userId={}, queued={}", userId, queued);
                chatPushService.pushError(userId, "实时语音上行过载，请重新开启");
                chatPushService.pushPetMotion(userId, "idle", "normal");
                close("send backlog overflow");
            }
            return;
        }
        synchronized (outboundLock) {
            outboundTail = outboundTail
                .exceptionally(error -> null)
                .thenCompose(ignored -> {
                    WebSocket socket = webSocketRef.get();
                    if (socket == null || closed.get()) {
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return socket.sendText(payload, true).thenApply(sentSocket -> null);
                })
                .whenComplete((ignored, error) -> outboundBacklog.decrementAndGet())
                .exceptionally(error -> {
                    if (!closed.get()) {
                        log.warn("发送 Qwen Realtime 事件失败：userId={}", userId, error);
                        chatPushService.pushError(userId, "实时语音发送失败，请重新开启");
                        chatPushService.pushPetMotion(userId, "idle", "normal");
                        close("send failure");
                    }
                    return null;
                });
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sanitizeVoice(String requestedVoice) {
        if (!hasText(requestedVoice)) {
            return aiProperties.getRealtimeVoice();
        }
        String trimmed = requestedVoice.trim();
        if (trimmed.length() > 64 || !trimmed.matches("[A-Za-z0-9_-]+")) {
            return aiProperties.getRealtimeVoice();
        }
        return trimmed;
    }

    private double clamp(Double value, double min, double max, double fallback) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String newEventId() {
        return "event_" + UUID.randomUUID();
    }

    /** Small indirection to keep exception handling out of event methods. */
    private static final class CompletableFutureSupport {
        private static void runAsync(Runnable runnable, Executor executor) {
            java.util.concurrent.CompletableFuture.runAsync(runnable, executor)
                    .exceptionally(error -> null);
        }
    }
}
