package com.zjkl.ai.chat.service;

import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SisterChatService {

    private final QwenStreamingChatModel qwenStreamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final EmotionService emotionService;
    private final PromptTemplateService promptTemplateService;
    private final PromptCacheService promptCacheService;
    private final ImageDescriptionService imageDescriptionService;

    private static final String SYSTEM_PROMPT_KEY = "character/prompt_1";
    private static final String TEMPLATE_KEY = "voice-chat";

    /**
     * 聊天结果
     */
    public record ChatResult(Flux<String> stream, CompletableFuture<String> imageDescFuture) {}

    /**
     * 语音聊天入口
     */
    public ChatResult chatWithVoice(String userInput, String memoryId, String imageUrl) {
        // 获取情绪
        EmotionalState current = emotionService.getUserEmotion(memoryId);
        String moodDesc = emotionService.getUserMoodDescription(memoryId);

        // 渲染输入
        String promptText = promptTemplateService.render(TEMPLATE_KEY, Map.of(
            "user_input", userInput,
            "mood_description", moodDesc,
            "pleasure", formatPad(current.getPleasure()),
            "arousal", formatPad(current.getArousal()),
            "dominance", formatPad(current.getDominance())
        ));

        log.debug("语音聊天: memoryId={}, moodDesc={}, P={}, A={}, D={}, hasImage={}",
            memoryId, moodDesc, current.getPleasure(), current.getArousal(), current.getDominance(), imageUrl != null && !imageUrl.isBlank());

        return chat(promptText, memoryId, imageUrl, moodDesc, current);
    }

    /**
     * 系统提示词
     */
    private String buildSystemPrompt(String moodDesc, EmotionalState current) {
        String characterPrompt = promptCacheService.getTemplate(SYSTEM_PROMPT_KEY);

        return characterPrompt + "\n\n【当前状态】\n" +
               "你现在的感觉：" + moodDesc + "\n" +
               "情绪数值：愉悦度=" + formatPad(current.getPleasure()) +
               ", 唤醒度=" + formatPad(current.getArousal()) +
               ", 支配感=" + formatPad(current.getDominance());
    }

    private String formatPad(Double value) {
        return String.format("%.3f", value != null ? value : 0.0);
    }

    /**
     * 流式聊天
     */
    public ChatResult chat(String promptText, String memoryId, String imageUrl) {
        EmotionalState current = emotionService.getUserEmotion(memoryId);
        String moodDesc = emotionService.getUserMoodDescription(memoryId);
        return chat(promptText, memoryId, imageUrl, moodDesc, current);
    }

    /**
     * 流式聊天
     */
    public ChatResult chat(String promptText, String memoryId, String imageUrl,
                           String moodDesc, EmotionalState current) {
        // 有图片则并行描述
        final CompletableFuture<String> imageDescFuture;
        if (imageUrl != null && !imageUrl.isBlank()) {
            imageDescFuture = CompletableFuture.supplyAsync(() -> {
                log.debug("开始 VLM 理解图片: {}", imageUrl);
                return imageDescriptionService.describe(imageUrl);
            });
            log.debug("已启动异步 VLM 任务，预计 1-3 秒完成");
        } else {
            imageDescFuture = null;
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(buildMessages(promptText, memoryId, imageUrl, moodDesc, current))
                .build();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        qwenStreamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                sink.tryEmitNext(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式聊天错误: memoryId={}", memoryId, error);
                sink.tryEmitError(error);
            }
        });

        return new ChatResult(sink.asFlux(), imageDescFuture);
    }

    private List<ChatMessage> buildMessages(String promptText, String memoryId, String imageUrl,
                                            String moodDesc, EmotionalState current) {
        List<ChatMessage> messagesToSend = new ArrayList<>();

        // 系统提示词
        String systemPrompt = buildSystemPrompt(moodDesc, current);
        messagesToSend.add(SystemMessage.from(systemPrompt));

        // 历史消息
        List<ChatMessage> historyMessages = chatMemoryProvider.get(memoryId).messages();
        if (!historyMessages.isEmpty()) {
            messagesToSend.addAll(historyMessages);
        }

        // 当前消息
        UserMessage userMessage;
        if (imageUrl != null && !imageUrl.isBlank()) {
            userMessage = UserMessage.from(
                    TextContent.from(promptText),
                    ImageContent.from(imageUrl, ImageContent.DetailLevel.AUTO)
            );
        } else {
            userMessage = UserMessage.from(promptText);
        }
        messagesToSend.add(userMessage);

        log.debug("组装消息: system=1, history={}, user=1, image={}",
                historyMessages.size(), imageUrl != null && !imageUrl.isBlank());

        return messagesToSend;
    }

}
