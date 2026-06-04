package com.zjkl.emotion.util;

import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.VoiceParams;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmResponseStreamParserTest {

    private final LlmResponseStreamParser parser = new LlmResponseStreamParser();

    @Test
    void parsesVoiceReplyAndDeltaFromChunkedStream() {
        var result = parser.parse(Flux.just(
                "{\"voice_params\":{\"volume\":60,\"speechRate\":1.0,",
                "\"pitchRate\":1.0,\"instruction\":\"温和地\"},",
                "\"reply\":\"你好呀，今天也要开心。\",",
                "\"delta_emotion\":{\"deltaP\":0.1,\"deltaA\":0.2,\"deltaD\":0.0}}"
        ).delayElements(Duration.ofMillis(5)));

        VoiceParams voiceParams = result.getVoiceParams().block(Duration.ofSeconds(2));
        DeltaEmotion deltaEmotion = result.getDeltaEmotion().block(Duration.ofSeconds(2));

        assertNotNull(voiceParams);
        assertNotNull(deltaEmotion);
        assertEquals(60, voiceParams.getVolume());
        assertEquals(0.1, deltaEmotion.getDeltaP());
        assertEquals("你好呀，今天也要开心。",
                result.getReplyStream().reduce(new StringBuilder(), StringBuilder::append).map(StringBuilder::toString)
                        .block(Duration.ofSeconds(2)));
    }

    @Test
    void failsReplyStreamWhenReplyExceedsMaxLength() {
        String longReply = "啊".repeat(20_001);
        String json = "{\"voice_params\":{\"volume\":60,\"speechRate\":1.0,\"pitchRate\":1.0,\"instruction\":\"温和地\"},"
                + "\"reply\":\"" + longReply + "\","
                + "\"delta_emotion\":{\"deltaP\":0.1,\"deltaA\":0.2,\"deltaD\":0.0}}";

        var result = parser.parse(Flux.just(json).delayElements(Duration.ofMillis(5)));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> result.getReplyStream().reduce(new StringBuilder(), StringBuilder::append).map(StringBuilder::toString)
                        .block(Duration.ofSeconds(2)));
        String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
        assertNotNull(message);
        assertEquals(true, message.contains("reply exceeds max length"));
    }

    @Test
    void defaultsVoiceParamsWhenVoiceJsonExceedsMaxLength() {
        String hugeInstruction = "a".repeat(4_200);
        String json = "{\"voice_params\":{\"volume\":60,\"speechRate\":1.0,\"pitchRate\":1.0,\"instruction\":\""
                + hugeInstruction + "\"},"
                + "\"reply\":\"你好\","
                + "\"delta_emotion\":{\"deltaP\":0.1,\"deltaA\":0.2,\"deltaD\":0.0}}";

        var result = parser.parse(Flux.just(json).delayElements(Duration.ofMillis(5)));

        VoiceParams voiceParams = result.getVoiceParams().block(Duration.ofSeconds(2));
        assertNotNull(voiceParams);
        assertEquals(60, voiceParams.getVolume());
        assertEquals("温和地", voiceParams.getInstruction());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> result.getReplyStream().reduce(new StringBuilder(), StringBuilder::append).map(StringBuilder::toString)
                        .block(Duration.ofSeconds(2)));
        String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
        assertNotNull(message);
        assertEquals(true, message.contains("voice_params JSON exceeds max length"));
    }
}
