package com.zjkl.emotion.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.VoiceParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Set;



@Slf4j
@Component
public class LlmResponseStreamParser {


    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_KEYWORD_LEN = 13;
    private static final int RING_BUF_SIZE = MAX_KEYWORD_LEN + 2;
    private static final int MAX_BUFFER_SIZE = 30;
    private static final int MIN_FLUSH_SIZE = 3;
    private static final Set<Character> PUNCTUATION = Set.of(
        '。', '！', '？', '；', '…', '\n', '.', '，', ','
    );


    private enum ParseState {
        FINDING_VOICE, FINDING_VOICE_COLON, FINDING_VOICE_BRACE, PARSING_VOICE,
        FINDING_REPLY, FINDING_REPLY_COLON, FINDING_REPLY_QUOTE, PARSING_REPLY,
        FINDING_EMOTION, FINDING_EMOTION_COLON, FINDING_EMOTION_BRACE, PARSING_EMOTION,
        DONE
    }


    public static class ParsedResult {
        private final Mono<VoiceParams> voiceParams;
        private final Flux<String> replyStream;
        private final Mono<DeltaEmotion> deltaEmotion;

        public ParsedResult(Mono<VoiceParams> voiceParams,
                           Flux<String> replyStream,
                           Mono<DeltaEmotion> deltaEmotion) {
            this.voiceParams = voiceParams;
            this.replyStream = replyStream;
            this.deltaEmotion = deltaEmotion;
        }

        public Mono<VoiceParams> getVoiceParams() { return voiceParams; }
        public Flux<String> getReplyStream() { return replyStream; }
        public Mono<DeltaEmotion> getDeltaEmotion() { return deltaEmotion; }
    }


    public ParsedResult parse(Flux<String> llmStream) {
        Sinks.One<VoiceParams> voiceParamsSink = Sinks.one();
        Sinks.Many<String> replySink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.One<DeltaEmotion> deltaSink = Sinks.one();

        ParseState[] state = {ParseState.FINDING_VOICE};

        int[] braceCount = {0};
        StringBuilder jsonBuilder = new StringBuilder();

        // reply 解析上下文
        boolean[] isEscapeNext = {false};
        StringBuilder replyBuf = new StringBuilder();

        // AC 自动机（每次 parse 独立创建，避免并发问题）
        AhoCorasickMatcher acAutomaton = new AhoCorasickMatcher(
            "voice_params", "reply", "delta_emotion"
        );

        // 环形缓冲区
        char[] ringBuf = new char[RING_BUF_SIZE];
        int[] ringPos = {0};

        llmStream
            .doOnNext(chunk -> {
                for (int i = 0; i < chunk.length(); i++) {
                    char c = chunk.charAt(i);

                    // 写入环形缓冲区（原始字符）
                    ringBuf[ringPos[0] % RING_BUF_SIZE] = c;
                    ringPos[0]++;

                    switch (state[0]) {

                        case FINDING_VOICE: {
                            String matched = acAutomaton.feedChar(Character.toLowerCase(c));
                            if ("voice_params".equals(matched)
                                    && isPrecededByQuote(matched.length(), ringBuf, ringPos[0])) {
                                state[0] = ParseState.FINDING_VOICE_COLON;
                                log.debug("AC 命中 voice_params，等待冒号");
                            }
                            // 否则忽略（用户内容误触发）
                            break;
                        }

                        case FINDING_VOICE_COLON: {
                            if (c == ':') {
                                state[0] = ParseState.FINDING_VOICE_BRACE;
                            }
                            break;
                        }

                        case FINDING_VOICE_BRACE: {
                            if (c == '{') {
                                jsonBuilder.setLength(0);
                                jsonBuilder.append(c);
                                braceCount[0] = 1;
                                state[0] = ParseState.PARSING_VOICE;
                                log.debug("进入 PARSING_VOICE");
                            }
                            break;
                        }

                        case PARSING_VOICE: {
                            jsonBuilder.append(c);
                            if (c == '{') braceCount[0]++;
                            else if (c == '}') braceCount[0]--;
                            if (braceCount[0] == 0) {
                                try {
                                    VoiceParams params = objectMapper.readValue(jsonBuilder.toString(), VoiceParams.class);
                                    voiceParamsSink.tryEmitValue(params);
                                    log.debug("voice_params 解析完成：volume={}, speechRate={}, pitchRate={}",
                                        params.getVolume(), params.getSpeechRate(), params.getPitchRate());
                                } catch (JsonProcessingException e) {
                                    log.error("解析 voice_params 失败", e);
                                }
                                braceCount[0] = 0;
                                state[0] = ParseState.FINDING_REPLY;
                                acAutomaton.reset(); // 清空内部状态，准备匹配 reply
                                log.debug("voice_params 完成，AC reset，进入 FINDING_REPLY");
                            }
                            break;
                        }

                        case FINDING_REPLY: {
                            String matched = acAutomaton.feedChar(Character.toLowerCase(c));
                            if ("reply".equals(matched)
                                    && isPrecededByQuote(matched.length(), ringBuf, ringPos[0])) {
                                state[0] = ParseState.FINDING_REPLY_COLON;
                                log.debug("AC 命中 reply，等待冒号");
                            }
                            break;
                        }

                        case FINDING_REPLY_COLON: {
                            if (c == ':') {
                                state[0] = ParseState.FINDING_REPLY_QUOTE;
                            }
                            break;
                        }

                        case FINDING_REPLY_QUOTE: {
                            if (c == '"') {
                                isEscapeNext[0] = false;
                                state[0] = ParseState.PARSING_REPLY;
                                log.debug("进入 PARSING_REPLY");
                            }
                            break;
                        }

                        case PARSING_REPLY: {
                            if (isEscapeNext[0]) {
                                replyBuf.append(c);
                                isEscapeNext[0] = false;
                            } else if (c == '\\') {
                                isEscapeNext[0] = true;
                            } else if (c == '"') {
                                flushReplyBuffer(replyBuf, replySink);
                                state[0] = ParseState.FINDING_EMOTION;
                                acAutomaton.reset();
                                log.debug("reply 闭合，AC reset，进入 FINDING_EMOTION");
                            } else {
                                replyBuf.append(c);
                                if (PUNCTUATION.contains(c) && replyBuf.length() >= MIN_FLUSH_SIZE) {
                                    flushReplyBuffer(replyBuf, replySink);
                                } else if (replyBuf.length() >= MAX_BUFFER_SIZE) {
                                    flushReplyBuffer(replyBuf, replySink);
                                }
                            }
                            break;
                        }

                        case FINDING_EMOTION: {
                            if (c == ',' || c == '\n' || c == ' ' || c == '\r' || c == '\t') {
                                break;
                            }
                            String matched = acAutomaton.feedChar(Character.toLowerCase(c));
                            if ("delta_emotion".equals(matched)
                                    && isPrecededByQuote(matched.length(), ringBuf, ringPos[0])) {
                                state[0] = ParseState.FINDING_EMOTION_COLON;
                                log.debug("AC 命中 delta_emotion，等待冒号");
                            }
                            break;
                        }

                        case FINDING_EMOTION_COLON: {
                            if (c == ':') {
                                state[0] = ParseState.FINDING_EMOTION_BRACE;
                            }
                            break;
                        }

                        case FINDING_EMOTION_BRACE: {
                            if (c == '{') {
                                jsonBuilder.setLength(0);
                                jsonBuilder.append(c);
                                braceCount[0] = 1;
                                state[0] = ParseState.PARSING_EMOTION;
                                log.debug("进入 PARSING_EMOTION");
                            }
                            break;
                        }

                        case PARSING_EMOTION: {
                            jsonBuilder.append(c);
                            if (c == '{') braceCount[0]++;
                            else if (c == '}') braceCount[0]--;
                            if (braceCount[0] == 0) {
                                try {
                                    DeltaEmotion delta = objectMapper.readValue(jsonBuilder.toString(), DeltaEmotion.class);
                                    deltaSink.tryEmitValue(delta);
                                    log.debug("delta_emotion 解析完成：deltaP={}, deltaA={}, deltaD={}",
                                        delta.getDeltaP(), delta.getDeltaA(), delta.getDeltaD());
                                } catch (JsonProcessingException e) {
                                    log.error("解析 delta_emotion 失败", e);
                                }
                                braceCount[0] = 0;
                                state[0] = ParseState.DONE;
                                log.debug("全部解析完成");
                            }
                            break;
                        }

                        case DONE:
                            break;
                    }
                }
            })
            .doOnComplete(() -> {
                log.info("llmStream doOnComplete 触发，state={}", state[0]);
                if (state[0] == ParseState.PARSING_REPLY) {
                    log.error("LLM 流结束但 reply 未闭合，phase: {}", state[0]);
                    replySink.tryEmitError(new IllegalStateException("LLM 流结束但 reply 未闭合"));
                } else {
                    log.info("LLM 流完成，phase: {}", state[0]);
                }
                replySink.tryEmitComplete();
            })
            .doOnError(error -> {
                log.error("LLM 流错误", error);
                voiceParamsSink.tryEmitError(error);
                replySink.tryEmitError(error);
                deltaSink.tryEmitError(error);
            })
            .subscribe();

        return new ParsedResult(
            voiceParamsSink.asMono(),
            replySink.asFlux(),
            deltaSink.asMono()
        );
    }


    /**
     * 将回复缓冲区内容批量 emit，清空缓冲区。
     * 缓冲为空时直接跳过。
     */
    private void flushReplyBuffer(StringBuilder buf, Sinks.Many<String> sink) {
        if (buf.length() == 0) return;
        String segment = buf.toString();
        buf.setLength(0);
        Sinks.EmitResult result = sink.tryEmitNext(segment);
        if (result != Sinks.EmitResult.OK) {
            log.warn("reply segment emit 失败: {}, segment: {}", result, segment);
        } else {
            log.debug("reply emit segment: len={}, text={}", segment.length(), segment);
        }
    }


    /**
     * 边界检查：回溯环形缓冲区，验证关键字前一个字符是否为双引号 "。
     * 只有前一个字符是 " 时才视为有效的 JSON Key，防止用户英文内容误触发。
     *
     * @param keywordLen  命中关键字的长度
     * @param ringBuf    环形缓冲区（存储原始字符）
     * @param currentPos 当前写入位置（已自增）
     * @return true 表示前一个字符是 "
     */
    private boolean isPrecededByQuote(int keywordLen, char[] ringBuf, int currentPos) {
        int lookback = currentPos - keywordLen - 1;
        if (lookback < 0) return false;
        return ringBuf[lookback % ringBuf.length] == '"';
    }
}
