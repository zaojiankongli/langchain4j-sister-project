package com.zjkl.ai.chat.service;

import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.common.constant.PromptConstants;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.memory.service.GraphSnapshotService;
import com.zjkl.memory.service.SummaryMemoryService;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.user.service.UserProfileService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天服务
 */
@Slf4j
@Service
public class SisterChatService {

    private final QwenStreamingChatModel qwenStreamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final EmotionService emotionService;
    private final PromptTemplateService promptTemplateService;
    private final PromptCacheService promptCacheService;
    private final ImageDescriptionService imageDescriptionService;
    private final UserProfileService userProfileService;
    private final SummaryMemoryService summaryMemoryService;
    private final GraphSnapshotService graphSnapshotService;
    private final GraphQueryService graphQueryService;
    private final RagRouter ragRouter;
    private final java.util.concurrent.Executor asyncExecutor;

    public SisterChatService(QwenStreamingChatModel qwenStreamingChatModel,
                             ChatMemoryProvider chatMemoryProvider,
                             EmotionService emotionService,
                             PromptTemplateService promptTemplateService,
                             PromptCacheService promptCacheService,
                             ImageDescriptionService imageDescriptionService,
                             UserProfileService userProfileService,
                             SummaryMemoryService summaryMemoryService,
                             GraphSnapshotService graphSnapshotService,
                             GraphQueryService graphQueryService,
                             RagRouter ragRouter,
                             java.util.concurrent.Executor asyncExecutor) {
        this.qwenStreamingChatModel = qwenStreamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.emotionService = emotionService;
        this.promptTemplateService = promptTemplateService;
        this.promptCacheService = promptCacheService;
        this.imageDescriptionService = imageDescriptionService;
        this.userProfileService = userProfileService;
        this.summaryMemoryService = summaryMemoryService;
        this.graphSnapshotService = graphSnapshotService;
        this.graphQueryService = graphQueryService;
        this.ragRouter = ragRouter;
        this.asyncExecutor = asyncExecutor;
    }

    private static final String SYSTEM_PROMPT_KEY = PromptConstants.CHARACTER_SYSTEM_PROMPT;
    private static final String TEMPLATE_KEY = PromptConstants.VOICE_CHAT_TEMPLATE;

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

        // 获取用户画像（轻量查询，仅 username/hobbies/bio）
        String userName = "哥哥";
        String userHobbies = "未知";
        String userBio = "";
        try {
            String[] chatProfile = userProfileService.getProfileForChat(memoryId);
            if (chatProfile != null) {
                userName = chatProfile[0] != null ? chatProfile[0] : "哥哥";
                userHobbies = chatProfile[1] != null ? chatProfile[1] : "未知";
                userBio = chatProfile[2] != null ? chatProfile[2] : "";
            }
        } catch (Exception e) {
            log.warn("获取用户画像失败，使用默认值: memoryId={}", memoryId, e);
        }

        // 渲染输入
        String promptText = promptTemplateService.render(TEMPLATE_KEY, Map.of(
            "user_input", userInput,
            "mood_description", moodDesc,
            "pleasure", formatPad(current.getPleasure()),
            "arousal", formatPad(current.getArousal()),
            "dominance", formatPad(current.getDominance()),
            "userName", userName,
            "userHobbies", userHobbies,
            "userProfile", userBio
        ));

        log.debug("语音聊天: memoryId={}, userName={}, moodDesc={}, P={}, A={}, D={}, hasImage={}",
            memoryId, userName, moodDesc, current.getPleasure(), current.getArousal(), current.getDominance(), imageUrl != null && !imageUrl.isBlank());

        // RAG：路由器判断是否需要搜索记忆 + 提取过滤条件
        String memoryBlock = "";
        try {
            // 获取最近消息作为路由上下文
            var chatMemory = chatMemoryProvider.get(memoryId);
            java.util.List<String> recentMessages = new java.util.ArrayList<>();
            if (chatMemory != null) {
                var msgs = chatMemory.messages();
                int startIdx = Math.max(0, msgs.size() - 60); // 最近 60 条（≈30 轮对话）
                for (int i = startIdx; i < msgs.size() && recentMessages.size() < 30; i++) {
                    var m = msgs.get(i);
                    if (m instanceof dev.langchain4j.data.message.UserMessage u) {
                        recentMessages.add("用户：" + u.singleText());
                    } else if (m instanceof dev.langchain4j.data.message.AiMessage a) {
                        recentMessages.add("Zeeva：" + (a.text() != null ? a.text() : ""));
                    }
                }
            }
            RouterResult route = ragRouter.analyzeQuery(userInput, recentMessages);
            if (route.needMemorySearch()) {
                memoryBlock = summaryMemoryService.buildMemoryBlock(memoryId, userInput, route.toFilters());
                log.debug("RAG 记忆块注入：userId={}, length={}, dateHint={}, topicHint={}",
                        memoryId, memoryBlock.length(), route.dateHint(), route.topicHint());
            }

            // 仅在路由器判断需要图搜索时才获取图快照和图关系块，节省 token 和延迟
            String graphSnapshot = "";
            String graphBlock = "";
            if (route.needGraphSearch()) {
                graphSnapshot = graphSnapshotService.getSnapshot(memoryId);
                graphBlock = graphQueryService.buildGraphBlock(memoryId, userInput);
            }

            memoryBlock = mergeGraphAndMemoryBlocks(route.primarySource(), graphSnapshot, graphBlock, memoryBlock);
            log.debug("图上下文注入：userId={}, snapshot={}, graphBlock={}, memoryBlock={}",
                    memoryId,
                    !graphSnapshot.isBlank(),
                    !graphBlock.isBlank(),
                    !memoryBlock.isBlank());
        } catch (Exception e) {
            log.warn("RAG 路由/记忆搜索失败，跳过记忆注入: {}", e.getMessage());
        }

        return chat(promptText, memoryId, imageUrl, moodDesc, current, userName, userHobbies, userBio, memoryBlock);
    }

    /**
     * 系统提示词
     */
    private String buildSystemPrompt(String moodDesc, EmotionalState current, String userName, String userHobbies, String userBio) {
        String characterPrompt = promptCacheService.getTemplate(SYSTEM_PROMPT_KEY);

        return characterPrompt + "\n\n" +
               "【你的哥哥/姐姐】\n" +
               "用户名：" + userName + "\n" +
               (userHobbies != null && !userHobbies.isEmpty() ? "兴趣爱好：" + userHobbies + "\n" : "") +
               (userBio != null && !userBio.isEmpty() ? "简介：" + userBio + "\n" : "") +
               "\n【当前状态】\n" +
               "你现在的感觉：" + moodDesc + "\n" +
               "情绪数值：愉悦度=" + formatPad(current.getPleasure()) +
               ", 唤醒度=" + formatPad(current.getArousal()) +
               ", 支配感=" + formatPad(current.getDominance());
    }

    private String formatPad(Double value) {
        return String.format("%.3f", value != null ? value : 0.0);
    }

    /**
     * 流式聊天（自动获取情绪状态和默认用户画像）
     */
    public ChatResult chat(String promptText, String memoryId, String imageUrl) {
        EmotionalState current = emotionService.getUserEmotion(memoryId);
        String moodDesc = emotionService.getUserMoodDescription(memoryId);
        return chat(promptText, memoryId, imageUrl, moodDesc, current, "哥哥", "", "", "");
    }

    /**
     * 流式聊天（指定用户画像，无记忆块）
     */
    public ChatResult chat(String promptText, String memoryId, String imageUrl,
                           String moodDesc, EmotionalState current,
                           String userName, String userHobbies, String userBio) {
        return chat(promptText, memoryId, imageUrl, moodDesc, current, userName, userHobbies, userBio, "");
    }

    /**
     * 流式聊天（完整参数）
     */
    public ChatResult chat(String promptText, String memoryId, String imageUrl,
                           String moodDesc, EmotionalState current,
                           String userName, String userHobbies, String userBio,
                           String memoryBlock) {
        // 有图片则并行描述
        final CompletableFuture<String> imageDescFuture;
        if (imageUrl != null && !imageUrl.isBlank()) {
            imageDescFuture = CompletableFuture.supplyAsync(() -> {
                log.debug("开始 VLM 理解图片: {}", imageUrl);
                return imageDescriptionService.describe(imageUrl);
            }, asyncExecutor);
            log.debug("已启动异步 VLM 任务，预计 1-3 秒完成");
        } else {
            imageDescFuture = null;
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(buildMessages(promptText, memoryId, imageUrl, moodDesc, current, userName, userHobbies, userBio, memoryBlock))
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
        return buildMessages(promptText, memoryId, imageUrl, moodDesc, current, "哥哥", "", "", "");
    }

    private List<ChatMessage> buildMessages(String promptText, String memoryId, String imageUrl,
                                            String moodDesc, EmotionalState current,
                                            String userName, String userHobbies, String userBio,
                                            String memoryBlock) {
        List<ChatMessage> messagesToSend = new ArrayList<>();

        // 系统提示词（含用户画像）
        String systemPrompt = buildSystemPrompt(moodDesc, current, userName, userHobbies, userBio);
        messagesToSend.add(SystemMessage.from(systemPrompt));

        // RAG 记忆块
        if (memoryBlock != null && !memoryBlock.isEmpty()) {
            messagesToSend.add(SystemMessage.from(memoryBlock));
        }

        // 历史消息
        var memory = chatMemoryProvider.get(memoryId);
        List<ChatMessage> historyMessages = (memory != null) ? memory.messages() : Collections.emptyList();
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

        log.debug("组装消息: system=1, memory_block={}, history={}, user=1, image={}",
                memoryBlock != null && !memoryBlock.isEmpty(), historyMessages.size(), imageUrl != null && !imageUrl.isBlank());

        return messagesToSend;
    }

    private String mergeGraphAndMemoryBlocks(String primarySource, String graphSnapshot, String graphBlock, String memoryBlock) {
        StringBuilder builder = new StringBuilder();

        if ("memory".equalsIgnoreCase(primarySource)) {
            appendBlock(builder, memoryBlock);
            appendBlock(builder, graphBlock);
            appendBlock(builder, wrapSnapshot(graphSnapshot));
        } else if ("graph".equalsIgnoreCase(primarySource)) {
            appendBlock(builder, graphBlock);
            appendBlock(builder, wrapSnapshot(graphSnapshot));
            appendBlock(builder, memoryBlock);
        } else {
            appendBlock(builder, wrapSnapshot(graphSnapshot));
            appendBlock(builder, graphBlock);
            appendBlock(builder, memoryBlock);
        }
        return builder.toString();
    }

    private void appendBlock(StringBuilder builder, String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(block.trim());
    }

    private String wrapSnapshot(String graphSnapshot) {
        if (graphSnapshot == null || graphSnapshot.isBlank()) {
            return "";
        }
        return "【图谱快照】\n" + graphSnapshot.trim();
    }

}
