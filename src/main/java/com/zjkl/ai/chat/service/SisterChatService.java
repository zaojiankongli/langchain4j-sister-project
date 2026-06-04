package com.zjkl.ai.chat.service;

import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.common.constant.PromptConstants;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.memory.service.GraphSnapshotService;
import com.zjkl.memory.service.SummaryMemoryService;
import com.zjkl.memory.service.SummaryMemoryService.MemoryBlockResult;
import com.zjkl.ai.chat.service.GraphQueryService.GraphResult;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

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

    // ========== RAG 路由/融合参数常量 ==========
    /** 路由上下文：从历史消息尾部取多少条（≈30 轮对话） */
    private static final int ROUTE_CONTEXT_WINDOW = 60;
    /** 路由上下文：最多取多少条最近消息 */
    private static final int ROUTE_MAX_RECENT = 30;
    /** 跨路融合：memory topScore 领先 graph 多少才排前面 */
    private static final double SCORE_LEAD_THRESHOLD = 0.1;
    /** 句子级去重：两 block 总长度低于此值时跳过去重 */
    private static final int DEDUP_MIN_CHARS = 500;
    /** 句子级去重：字符 bigram Jaccard 相似度超过此值视为重复 */
    private static final double DEDUP_SIMILARITY_THRESHOLD = 0.65;

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
            log.debug("获取用户画像失败，使用默认值: memoryId={}", memoryId, e);
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
                int startIdx = Math.max(0, msgs.size() - ROUTE_CONTEXT_WINDOW);
                for (int i = startIdx; i < msgs.size() && recentMessages.size() < ROUTE_MAX_RECENT; i++) {
                    var m = msgs.get(i);
                    if (m instanceof dev.langchain4j.data.message.UserMessage u) {
                        recentMessages.add("用户：" + u.singleText());
                    } else if (m instanceof dev.langchain4j.data.message.AiMessage a) {
                        recentMessages.add("Zeeva：" + (a.text() != null ? a.text() : ""));
                    }
                }
            }
            RouterResult route = ragRouter.analyzeQuery(userInput, recentMessages);

            // 双路 RAG 并行执行（虚拟线程），单路时仅执行对应路
            try (var vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                // Memory RAG 异步执行
                final CompletableFuture<MemoryBlockResult> memoryFuture = route.needMemorySearch()
                    ? CompletableFuture.supplyAsync(
                        () -> summaryMemoryService.buildMemoryBlockWithScore(memoryId, userInput, route.toFilters()),
                        vThreadExecutor)
                    : CompletableFuture.completedFuture(MemoryBlockResult.empty());

                // Graph RAG 异步执行（snapshot 从 Redis 取，graphBlock 需要 LLM 调用）
                final CompletableFuture<String> snapshotFuture;
                final CompletableFuture<GraphResult> graphFuture;
                if (route.needGraphSearch()) {
                    snapshotFuture = CompletableFuture.supplyAsync(
                        () -> graphSnapshotService.getSnapshot(memoryId), vThreadExecutor);
                    graphFuture = CompletableFuture.supplyAsync(
                        () -> graphQueryService.buildGraphBlock(memoryId, userInput), vThreadExecutor);
                } else {
                    snapshotFuture = CompletableFuture.completedFuture("");
                    graphFuture = CompletableFuture.completedFuture(GraphResult.empty());
                }

                // 等待所有结果
                CompletableFuture.allOf(memoryFuture, snapshotFuture, graphFuture).join();

                MemoryBlockResult memResult = memoryFuture.get();
                String graphSnapshot = snapshotFuture.get();
                GraphResult graphResult = graphFuture.get();

                // 跨路融合排序 + 去重
                memoryBlock = mergeRagResults(route.primarySource(),
                        graphSnapshot, graphResult, memResult);

                log.debug("RAG 融合结果：userId={}, memoryScore={}, graphScore={}, snapshot={}, memoryBlock={}",
                        memoryId, memResult.topScore(), graphResult.topScore(),
                        !graphSnapshot.isBlank(), !memoryBlock.isBlank());
            }
        } catch (Exception e) {
            log.debug("RAG 路由/记忆搜索失败，跳过记忆注入: {}", e.getMessage());
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

    /**
     * 跨路 RAG 融合：根据检索评分决定排列顺序 + 句子级去重
     * <p>
     * 单路执行时直接使用对应结果；双路执行时按 topScore 排序（高分优先），
     * 并对后排列的 block 做句子级语义去重，避免冗余信息消耗 LLM token。
     */
    private String mergeRagResults(String primarySource, String graphSnapshot,
                                   GraphResult graphResult, MemoryBlockResult memResult) {
        String memoryBlock = memResult.block();
        String graphBlock = graphResult.block();

        // 单路场景：直接返回，无需融合
        if (isBlank(graphBlock) && isBlank(memoryBlock)) {
            return isBlank(graphSnapshot) ? "" : wrapSnapshot(graphSnapshot);
        }
        if (isBlank(graphBlock)) {
            return isBlank(memoryBlock) ? wrapSnapshot(graphSnapshot) : appendTwo(memoryBlock, wrapSnapshot(graphSnapshot));
        }
        if (isBlank(memoryBlock)) {
            return appendTwo(graphBlock, wrapSnapshot(graphSnapshot));
        }

        // 双路场景：按评分排序 + 句子级去重
        String first, second;
        if ("memory".equalsIgnoreCase(primarySource)) {
            first = memoryBlock;
            second = graphBlock;
        } else if ("graph".equalsIgnoreCase(primarySource)) {
            first = graphBlock;
            second = memoryBlock;
        } else {
            // "both" 或默认：评分高的优先（memory 领先 ≥ SCORE_LEAD_THRESHOLD 时排前面）
            if (memResult.topScore() >= graphResult.topScore() + SCORE_LEAD_THRESHOLD) {
                first = memoryBlock;
                second = graphBlock;
            } else {
                first = graphBlock;
                second = memoryBlock;
            }
        }

        // 句子级去重：从 second 中移除与 first 语义重叠的句子
        second = deduplicateSentences(second, first);

        StringBuilder result = new StringBuilder(first.trim());
        if (!second.isBlank()) {
            result.append("\n\n").append(second.trim());
        }
        String snapshot = wrapSnapshot(graphSnapshot);
        if (!snapshot.isBlank()) {
            result.append("\n\n").append(snapshot);
        }
        return result.toString();
    }

    /**
     * 从 candidate 中移除与 reference 语义重叠的句子（基于字符级 Jaccard 相似度）
     */
    private String deduplicateSentences(String candidate, String reference) {
        if (candidate == null || reference == null) return candidate;
        // 短文本无需去重
        if (candidate.length() + reference.length() < DEDUP_MIN_CHARS) return candidate;

        List<String> refSentences = splitSentences(reference);
        List<String> candSentences = splitSentences(candidate);

        List<String> kept = new ArrayList<>();
        for (String cs : candSentences) {
            if (cs.trim().length() < 8) {
                kept.add(cs); // 太短的句子（如标题、标记行）保留
                continue;
            }
            boolean isDuplicate = false;
            for (String rs : refSentences) {
                if (rs.trim().length() < 8) continue;
                if (sentenceSimilarity(cs, rs) > DEDUP_SIMILARITY_THRESHOLD) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                kept.add(cs);
            }
        }
        return String.join("", kept);
    }

    /** 按中文/英文句号、换行符切分句子，保留标点 */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            current.append(c);
            if (c == '。' || c == '.' || c == '\n' || c == '！' || c == '?' || c == '；') {
                String s = current.toString().trim();
                if (!s.isEmpty()) sentences.add(s);
                current = new StringBuilder();
            }
        }
        if (!current.isEmpty()) {
            String s = current.toString().trim();
            if (!s.isEmpty()) sentences.add(s);
        }
        return sentences;
    }

    /** 基于字符集合 Jaccard 相似度判断两个句子的重叠程度 */
    private double sentenceSimilarity(String a, String b) {
        String na = a.replaceAll("\\s+", "").toLowerCase();
        String nb = b.replaceAll("\\s+", "").toLowerCase();
        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        // 用字符 bigram 集合计算相似度（比单字符更准确）
        Set<String> setA = charBigrams(na);
        Set<String> setB = charBigrams(nb);
        Set<String> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new java.util.HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> charBigrams(String s) {
        Set<String> bigrams = new java.util.HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            bigrams.add(s.substring(i, i + 2));
        }
        if (s.length() == 1) bigrams.add(s);
        return bigrams;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String appendTwo(String first, String second) {
        if (isBlank(first)) return isBlank(second) ? "" : second.trim();
        if (isBlank(second)) return first.trim();
        return first.trim() + "\n\n" + second.trim();
    }

    private String wrapSnapshot(String graphSnapshot) {
        if (graphSnapshot == null || graphSnapshot.isBlank()) {
            return "";
        }
        return "【图谱快照】\n" + graphSnapshot.trim();
    }

}
