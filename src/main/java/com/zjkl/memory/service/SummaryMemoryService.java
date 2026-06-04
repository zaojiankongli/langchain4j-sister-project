package com.zjkl.memory.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zjkl.ai.chat.service.MemorySearchFilters;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.ai.summary.service.SummaryService;
import com.zjkl.common.config.properties.MilvusProperties;
import com.zjkl.common.context.UserContext;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.memory.constant.MemoryRedisKeys;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话摘要生成 + 混合检索记忆存储
 */
@Service
@Slf4j
public class SummaryMemoryService {

    // ========== 依赖注入 ==========
    private final StringRedisTemplate stringRedisTemplate;
    private final MilvusClientV2 milvusClientV2;
    private final String milvusCollectionName;
    private final EmbeddingModel embeddingModel;
    private final SummaryService summaryService;
    private final PromptTemplateService promptTemplateService;
    private final EmotionService emotionService;
    private final UserContext userContext;
    private final Gson gson = new Gson();

    // ========== 常量定义 ==========
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** RRF score 最低阈值 — k=60 时有效分值 ~0.02-0.03，极低分过滤噪声 */
    private static final double RRF_SCORE_THRESHOLD = 0.015;
    /** Max memories to return after filtering */
    private static final int MAX_MEMORIES = 3;
    /** TopK multiplier for retrieval (retrieve more, filter down) */
    private static final int RETRIEVAL_MULTIPLIER = 2;
    /** 单条记忆硬截断上限（compressText 使用） */
    private static final int COMPRESS_MAX_CHARS = 200;

    // ========== 模板 Key ==========
    private static final String FULL_SUMMARY_TEMPLATE_KEY = "summary-full";
    private static final String INCREMENTAL_SUMMARY_TEMPLATE_KEY = "summary-incremental";

    public SummaryMemoryService(StringRedisTemplate stringRedisTemplate,
                                MilvusClientV2 milvusClientV2,
                                MilvusProperties milvusProperties,
                                EmbeddingModel embeddingModel,
                                @Qualifier("summaryService") SummaryService summaryService,
                                PromptTemplateService promptTemplateService,
                                EmotionService emotionService,
                                UserContext userContext) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.milvusClientV2 = milvusClientV2;
        this.milvusCollectionName = milvusProperties.getMemoryCollectionName();
        this.embeddingModel = embeddingModel;
        this.summaryService = summaryService;
        this.promptTemplateService = promptTemplateService;
        this.emotionService = emotionService;
        this.userContext = userContext;
    }

    // ==================== 同步方法 ====================

    public String summarize(List<ChatMessage> messages, String characterCore) {
        String conversationText = messagesToText(messages);
        String prompt = promptTemplateService.render(FULL_SUMMARY_TEMPLATE_KEY,
            Map.of("conversation", conversationText));
        return summaryService.chat(characterCore, prompt);
    }

    public String summarizeWithPrevious(String previousSummary, List<ChatMessage> newMessages, String characterCore) {
        String newConversationText = messagesToText(newMessages);
        String prompt = promptTemplateService.render(INCREMENTAL_SUMMARY_TEMPLATE_KEY,
            Map.of(
                "previousSummary", previousSummary,
                "newConversation", newConversationText
            ));
        return summaryService.chat(characterCore, prompt);
    }

    // ==================== 异步方法 ====================

    @Async
    public void generateSummaryAsync(String memoryId, List<ChatMessage> messagesSnapshot) {
        log.info("开始异步生成用户 {} 的摘要，消息数：{}", memoryId, messagesSnapshot.size());

        try {
            EmotionalState currentEmotion = emotionService.getUserEmotion(memoryId);
            String moodLabel = emotionService.getUserMoodLabel(memoryId);
            String characterCore = promptTemplateService.render("character/core", Map.of(
                "pleasure", String.format("%.3f", currentEmotion.getPleasure()),
                "arousal", String.format("%.3f", currentEmotion.getArousal()),
                "dominance", String.format("%.3f", currentEmotion.getDominance()),
                "moodLabel", moodLabel != null ? moodLabel : ""
            ));

            String newSummary = generateNewSummary(memoryId, messagesSnapshot, characterCore);

            updateRedis(memoryId, newSummary, messagesSnapshot);

            updateMetadata(memoryId, messagesSnapshot.size());

            String title = "对话摘要 - " + LocalDate.now(ZONE).format(DATE_FORMATTER);
            String emotionLabel = extractEmotionLabel(newSummary);
            double sentimentScore = extractSentimentScore(newSummary);

            saveToVectorStore(memoryId, title, newSummary, emotionLabel, sentimentScore);

            log.info("用户 {} 的摘要生成完成，长度：{} 字，情绪={}, 情感分={}",
                    memoryId, newSummary.length(), emotionLabel, sentimentScore);

        } catch (Exception e) {
            log.error("用户 {} 的摘要生成失败（步骤将被跳过，下次对话压缩时重试）", memoryId, e);
        }
    }

    // ==================== 混合检索 ====================

    /**
     * 混合检索：dense(语义) + sparse(BM25全文) → RRF 融合 → 阈值过滤 → 去重 → 压缩
     * @return 压缩后的记忆文本列表（1-3 条）
     */
    public List<String> hybridSearchMemories(String userId, String query, int limit) {
        try {
            // 1. dense embedding
            long embedStart = System.currentTimeMillis();
            Embedding embedding = embeddingModel.embed(query).content();
            log.debug("embedding 耗时: {}ms, dim={}", System.currentTimeMillis() - embedStart, embedding.vector().length);
            float[] denseVector = embedding.vector();
            List<Float> denseList = new ArrayList<>(denseVector.length);
            for (float v : denseVector) denseList.add(v);

            int topK = limit * RETRIEVAL_MULTIPLIER;

            // 2. 构造两个 AnnSearchReq（v2.6.x: topK 控制每个向量字段的召回数）
            String userFilter = "user_id == \"" + userId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            List<AnnSearchReq> searchRequests = new ArrayList<>();
            searchRequests.add(AnnSearchReq.builder()
                    .vectorFieldName("dense_vector")
                    .vectors(Collections.singletonList(new FloatVec(denseList)))
                    .metricType(IndexParam.MetricType.IP)
                    .topK(topK)
                    .filter(userFilter)
                    .params("{\"nprobe\": 16}")  // 调优搜索精度；默认 1，16 平衡精度/性能
                    .build());
            searchRequests.add(AnnSearchReq.builder()
                    .vectorFieldName("sparse_vector")
                    .vectors(Collections.singletonList(new EmbeddedText(query)))
                    .metricType(IndexParam.MetricType.BM25)
                    .topK(topK)
                    .filter(userFilter)
                    .build());

            // 3. 混合检索（v2.6.x: limit 控制 RRF 后最终返回数）
            HybridSearchReq hybridReq = HybridSearchReq.builder()
                    .collectionName(milvusCollectionName)
                    .searchRequests(searchRequests)
                    .ranker(new RRFRanker(60))    // 工程最佳实践 k=60
                    .limit(topK)                   // v2.6.x 新增：RRF 后最终返回数
                    .outFields(Arrays.asList("id", "content", "title", "metadata"))
                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                    .build();

            SearchResp searchResp = milvusClientV2.hybridSearch(hybridReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                return List.of();
            }

            // 4. 后处理：阈值过滤 + 去重 + 压缩
            return postProcess(searchResults.get(0), userId, RRF_SCORE_THRESHOLD, limit);

        } catch (Exception e) {
            log.error("混合检索失败: userId={}, query={}", userId, query, e);
            return List.of();
        }
    }

    /** 用户记忆检索（带 userId 过滤，兼容旧 API） */
    public List<String> searchRelevantMemories(String userId, String query, int limit) {
        return hybridSearchMemories(userId, query, limit);
    }

    /**
     * 带过滤条件的混合检索：先将 topic_hint 拼入 query 增强召回，再在 postProcess 中用
     * date_hint / sentiment_hint 做 metadata 过滤。过滤器为空时等价于基础检索。
     */
    public List<String> hybridSearchMemories(String userId, String query,
                                              MemorySearchFilters filters, int limit) {
        // 增强 query：topic_hint 拼到 query 尾部提升 BM25 关键词命中
        String enhancedQuery = query;
        if (filters != null && filters.topicHint() != null) {
            enhancedQuery = query + " " + filters.topicHint();
        }
        List<String> raw = hybridSearchMemories(userId, enhancedQuery, limit);
        if (raw.isEmpty() || filters == null || filters.isEmpty()) {
            return raw;
        }
        // postProcess 已经做了 userId + score 过滤，这里追加 date/sentiment
        return raw.stream()
                .filter(r -> matchesDateHint(r, filters.dateHint()))
                .filter(r -> matchesSentimentHint(r, filters.sentimentHint()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 日期范围检索（兼容旧 API） */
    public List<String> searchMemoriesByDateRange(String userId, String query,
                                                   String startDate, String endDate, int limit) {
        List<String> results = hybridSearchMemories(userId, query, limit);
        return results.stream()
                .filter(r -> r.contains(startDate) || r.contains(endDate))
                .limit(MAX_MEMORIES)
                .collect(Collectors.toList());
    }

    /** 获取结构化记忆块（从 UserContext 取 userId） */
    public String buildMemoryBlock(String query) {
        String userId = userContext.getUserId();
        if (userId == null) return "";
        return buildMemoryBlock(userId, query, null);
    }

    /**
     * 获取结构化记忆块（专为 chat prompt 设计）
     * ≤3 条直接拼接；>3 条触发 LLM 压缩（分篇→聚合，一次调用）
     */
    public String buildMemoryBlock(String userId, String query) {
        return buildMemoryBlock(userId, query, null);
    }

    /**
     * 带过滤条件的记忆块构建
     */
    public String buildMemoryBlock(String userId, String query, MemorySearchFilters filters) {
        List<String> memories = hybridSearchMemories(userId, query, filters, MAX_MEMORIES * RETRIEVAL_MULTIPLIER);
        if (memories.isEmpty()) {
            return "";
        }
        String footer = "\n\n用这些记忆自然地融入回复，仿佛你真的记住了这些事。不要生硬地引用\"我记得之前...\"——让记忆成为你回应的底色。";
        // ≤3 条无需压缩
        if (memories.size() <= MAX_MEMORIES) {
            return "【哥哥和我的回忆】\n" + String.join("\n", memories) + footer;
        }
        // >3 条 → LLM 一次性分篇+聚合压缩
        String compressed = compressMemoriesWithLLM(memories);
        return "【哥哥和我的回忆】\n" + compressed + footer;
    }

    /** LLM 压缩记忆 */
    private String compressMemoriesWithLLM(List<String> memories) {
        String prompt = promptTemplateService.render("memory-compress",
                Map.of("memories", String.join("\n---\n", memories)));
        try {
            return summaryService.chat("", prompt);
        } catch (Exception e) {
            log.warn("LLM 记忆压缩失败，降级为硬截断: {}", e.getMessage());
            return memories.stream()
                    .limit(MAX_MEMORIES)
                    .collect(Collectors.joining("\n"));
        }
    }

    // ==================== 向量存储 ====================

    public void saveToVectorStore(String userId, String title, String summary,
                                   String emotionLabel, double sentimentScore) {
        try {
            // dense embedding
            Embedding embedding = embeddingModel.embed(summary).content();
            float[] denseVector = embedding.vector();
            List<Float> denseList = new ArrayList<>(denseVector.length);
            for (float v : denseVector) denseList.add(v);

            String today = LocalDate.now(ZONE).format(DATE_FORMATTER);
            String id = userId + ":" + today;

            // metadata JSON
            JsonObject metaJson = new JsonObject();
            metaJson.addProperty("create_time", today);
            double safeScore = Double.isNaN(sentimentScore) || Double.isInfinite(sentimentScore) ? 0.0 : sentimentScore;
            metaJson.addProperty("sentiment_score", String.format("%.3f", safeScore));
            metaJson.addProperty("emotion_label", emotionLabel != null ? emotionLabel : "平静");
            metaJson.addProperty("user_id", userId);

            // build insert row
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("content", summary);
            row.addProperty("title", title);
            row.add("dense_vector", gson.toJsonTree(denseList));
            row.addProperty("metadata", gson.toJson(metaJson));

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(milvusCollectionName)
                    .data(Collections.singletonList(row))
                    .build();
            milvusClientV2.insert(insertReq);

            log.debug("用户 {} 的摘要已存入 Milvus：title={}, 情绪={}, 情感分={}",
                    userId, title, emotionLabel, sentimentScore);
        } catch (Exception e) {
            log.error("用户 {} 的摘要存入 Milvus 失败（上层已有兜底）", userId, e);
        }
    }

    // ==================== 后处理 ====================

    private List<String> postProcess(List<SearchResp.SearchResult> results, String userId,
                                       double threshold, int maxResults) {
        return results.stream()
                // 1. RRF 分数阈值过滤
                .filter(r -> r.getScore() >= threshold)
                // 2. 解析 metadata 过滤 userId
                .filter(r -> matchesUserId(r, userId))
                // 3. 按分数降序
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                // 4. 去重（content 相似度）
                .collect(Collectors.toCollection(ArrayList::new))
                .stream()
                .filter(new DistinctByContent())
                // 5. 截取 topK
                .limit(maxResults)
                // 6. 压缩文本
                .map(this::formatMemory)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean matchesUserId(SearchResp.SearchResult result, String userId) {
        try {
            Object metaObj = result.getEntity().get("metadata");
            if (metaObj == null) return false; // no metadata → exclude (safer)
            String metaStr = metaObj.toString();
            // 使用精确边界匹配，避免 user1 匹配 user10
            String target = "\"user_id\":\"" + userId + "\"";
            int idx = metaStr.indexOf(target);
            if (idx < 0) return false;
            int afterEnd = idx + target.length();
            // 确保后面不是数字或字母（即不是 user10/user1a 等更长的 ID）
            if (afterEnd < metaStr.length()) {
                char next = metaStr.charAt(afterEnd);
                return !Character.isLetterOrDigit(next);
            }
            return true;
        } catch (Exception e) {
            return false; // parse error → exclude rather than include
        }
    }

    private String formatMemory(SearchResp.SearchResult result) {
        try {
            String content = (String) result.getEntity().get("content");
            if (content == null || content.isBlank()) return "";
            String compressed = compressText(content, COMPRESS_MAX_CHARS);

            // Gson 解析 metadata 提取日期
            String date = "????.??.??";
            try {
                String metaStr = result.getEntity().get("metadata").toString();
                JsonObject meta = gson.fromJson(metaStr, JsonObject.class);
                if (meta != null && meta.has("create_time")) {
                    date = meta.get("create_time").getAsString().replace("-", ".");
                }
            } catch (Exception ignored) {
                log.debug("记忆元数据日期解析失败，使用默认值");
            }

            return date + " — " + compressed;
        } catch (Exception e) {
            return "";
        }
    }

    /** 文本压缩：截断到 maxChars 字符，从截断点往回找最近句号/逗号 */
    static String compressText(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        String truncated = text.substring(0, maxChars);
        int lastBreak = Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('，'));
        if (lastBreak > maxChars / 2) {
            return truncated.substring(0, lastBreak);
        }
        return truncated;
    }

    // ==================== 摘要解析 ====================

    private String extractEmotionLabel(String summary) {
        int start = summary.indexOf("【情感标签】");
        if (start < 0) return "平静";
        start += "【情感标签】".length();
        int end = indexOfNextSection(summary, start);
        return summary.substring(start, end).trim();
    }

    private double extractSentimentScore(String summary) {
        int start = summary.indexOf("【情感得分】");
        if (start < 0) return 0.0;
        start += "【情感得分】".length();
        int end = indexOfNextSection(summary, start);
        try {
            return Double.parseDouble(summary.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int indexOfNextSection(String text, int from) {
        int idx = text.indexOf("【", from);
        return idx < 0 ? text.length() : idx;
    }

    // ==================== 去重辅助 ====================

    private static class DistinctByContent implements java.util.function.Predicate<SearchResp.SearchResult> {
        private final Set<String> seen = new HashSet<>();
        @Override
        public boolean test(SearchResp.SearchResult r) {
            String content = (String) r.getEntity().get("content");
            if (content == null) return false;
            String fingerprint = content.length() > 80 ? content.substring(0, 80) : content;
            return seen.add(fingerprint);
        }
    }

    // ==================== Redis 辅助（不变） ====================

    private String generateNewSummary(String memoryId, List<ChatMessage> messagesSnapshot, String characterCore) {
        String previousSummary = stringRedisTemplate
                .opsForValue().get(MemoryRedisKeys.SUMMARY_KEY + memoryId);

        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            int fromIndex = Math.max(0, messagesSnapshot.size() - MemoryRedisKeys.INCREMENTAL_SUMMARY_WINDOW);
            List<ChatMessage> newMessages = new ArrayList<>(messagesSnapshot.subList(fromIndex, messagesSnapshot.size()));
            return summarizeWithPrevious(previousSummary, newMessages, characterCore);
        } else {
            return summarize(messagesSnapshot, characterCore);
        }
    }

    private void updateRedis(String memoryId, String newSummary, List<ChatMessage> messages) throws Exception {
        stringRedisTemplate.opsForValue().set(
            MemoryRedisKeys.SUMMARY_KEY + memoryId,
            newSummary,
            MemoryRedisKeys.EXPIRATION_1_DAY
        );

        List<ChatMessage> compressed = buildCompressedMessages(newSummary, messages);
        String json = ChatMessageSerializer.messagesToJson(compressed);
        stringRedisTemplate.opsForValue().set(
            MemoryRedisKeys.HISTORY_KEY + memoryId,
            json,
            MemoryRedisKeys.EXPIRATION_1_DAY
        );
    }

    private void updateMetadata(String memoryId, int size) {
        stringRedisTemplate.opsForValue().set(
            MemoryRedisKeys.LAST_COMPRESSED_SIZE_KEY + memoryId,
            String.valueOf(size),
            MemoryRedisKeys.EXPIRATION_7_DAYS
        );
    }

    private List<ChatMessage> buildCompressedMessages(String summary, List<ChatMessage> messages) {
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(SystemMessage.from("【对话摘要】" + summary));

        int startIndex = Math.max(0, messages.size() - MemoryRedisKeys.KEEP_RECENT_COUNT);
        compressed.addAll(messages.subList(startIndex, messages.size()));

        return compressed;
    }

    private String messagesToText(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::messageToText)
                .collect(Collectors.joining("\n"));
    }

    private String messageToText(ChatMessage msg) {
        return switch (msg) {
            case UserMessage u -> "哥哥：" + u.singleText();
            case AiMessage a -> "妹妹：" + a.text();
            case SystemMessage s -> "[系统] " + s.text();
            default -> "";
        };
    }

    // 保留旧 saveToVectorStore 签名兼容（无 sentiment 参数时用默认值）
    public void saveToVectorStore(String userId, String title, String summary) {
        saveToVectorStore(userId, title, summary, "平静", 0.0);
    }

    // ==================== 过滤器辅助 ====================

    /**
     * 用记忆文本粗略匹配 date_hint（子串匹配 create_time）
     */
    private boolean matchesDateHint(String memory, String dateHint) {
        if (dateHint == null) return true;
        // 记忆格式："2026.06.02 — 聊了..." → date part 是前 10 字符
        String datePart = memory.length() >= 10 ? memory.substring(0, 10) : memory;
        String normalized = dateHint.replace("年", ".").replace("月", ".").replace("日", "");
        return datePart.contains(normalized);
    }

    /**
     * 用记忆文本粗略匹配 sentiment_hint
     */
    private boolean matchesSentimentHint(String memory, String sentimentHint) {
        if (sentimentHint == null) return true;
        // 简单子串匹配；后续可升级为从 metadata 解析 emotion_label
        return memory.contains(sentimentHint);
    }
}
