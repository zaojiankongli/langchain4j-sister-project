package com.zjkl.memory.service;

import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.ai.summary.service.SummaryService;
import com.zjkl.memory.constant.MemoryRedisKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话摘要生成
 */
@Service
@Slf4j
public class SummaryMemoryService {
    
    // ========== 依赖注入 ==========
    private final StringRedisTemplate stringRedisTemplate;
    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SummaryService summaryService;
    private final PromptTemplateService promptTemplateService;
    
    // ========== 常量定义 ==========
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    
    // ========== 模板 Key ==========
    private static final String FULL_SUMMARY_TEMPLATE_KEY = "summary-full";
    private static final String INCREMENTAL_SUMMARY_TEMPLATE_KEY = "summary-incremental";

    public SummaryMemoryService(StringRedisTemplate stringRedisTemplate,
                                MilvusEmbeddingStore milvusEmbeddingStore,
                                EmbeddingModel embeddingModel,
                                @Qualifier("summaryService") SummaryService summaryService,
                                PromptTemplateService promptTemplateService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.milvusEmbeddingStore = milvusEmbeddingStore;
        this.embeddingModel = embeddingModel;
        this.summaryService = summaryService;
        this.promptTemplateService = promptTemplateService;
    }

    // ==================== 同步方法 ====================

    /**
     * 全量摘要
     */
    public String summarize(List<ChatMessage> messages) {
        String conversationText = messagesToText(messages);
        String prompt = promptTemplateService.render(FULL_SUMMARY_TEMPLATE_KEY, 
            Map.of("conversation", conversationText));
        return summaryService.chat(prompt);
    }
    
    /**
     * 增量摘要
     */
    public String summarizeWithPrevious(String previousSummary, List<ChatMessage> newMessages) {
        String newConversationText = messagesToText(newMessages);
        String prompt = promptTemplateService.render(INCREMENTAL_SUMMARY_TEMPLATE_KEY, 
            Map.of(
                "previousSummary", previousSummary,
                "newConversation", newConversationText
            ));
        return summaryService.chat(prompt);
    }

    // ==================== 异步方法 ====================

    /**
     * 异步生成摘要
     */
    @Async
    public void generateSummaryAsync(String memoryId, List<ChatMessage> messagesSnapshot) {
        log.info("开始异步生成用户 {} 的摘要，消息数：{}", memoryId, messagesSnapshot.size());
        
        try {
            String newSummary = generateNewSummary(memoryId, messagesSnapshot);
            
            updateRedis(memoryId, newSummary, messagesSnapshot);
            
            updateMetadata(memoryId, messagesSnapshot.size());
            
            String title = "对话摘要 - " + LocalDate.now(ZONE).format(DATE_FORMATTER);
            saveToVectorStore(memoryId, title, newSummary);
            
            log.info("用户 {} 的摘要生成完成，长度：{} 字", memoryId, newSummary.length());
            
        } catch (Exception e) {
            log.error("用户 {} 的摘要生成失败", memoryId, e);
        }
    }
    
    /** 生成新摘要 */
    private String generateNewSummary(String memoryId, List<ChatMessage> messagesSnapshot) {
        String previousSummary = stringRedisTemplate
                .opsForValue().get(MemoryRedisKeys.SUMMARY_KEY + memoryId);
        
        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            int fromIndex = Math.max(0, messagesSnapshot.size() - MemoryRedisKeys.INCREMENTAL_SUMMARY_WINDOW);
            List<ChatMessage> newMessages = new ArrayList<>(messagesSnapshot.subList(fromIndex, messagesSnapshot.size()));
            return summarizeWithPrevious(previousSummary, newMessages);
        } else {
            return summarize(messagesSnapshot);
        }
    }
    
    /** 更新 Redis */
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
    
    /** 更新元数据 */
    private void updateMetadata(String memoryId, int size) {
        stringRedisTemplate.opsForValue().set(
            MemoryRedisKeys.LAST_COMPRESSED_SIZE_KEY + memoryId,
            String.valueOf(size),
            MemoryRedisKeys.EXPIRATION_7_DAYS
        );
    }

    /**
     * 压缩消息
     */
    private List<ChatMessage> buildCompressedMessages(String summary, List<ChatMessage> messages) {
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(SystemMessage.from("【对话摘要】" + summary));
        
        int startIndex = Math.max(0, messages.size() - MemoryRedisKeys.KEEP_RECENT_COUNT);
        compressed.addAll(messages.subList(startIndex, messages.size()));
        
        return compressed;
    }
    
    // ==================== 向量检索 ====================

    /**
     * 向量检索
     */
    public List<String> searchMemories(String query, int limit, Filter filter) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(limit);
            if (filter != null) {
                builder.filter(filter);
            }
            EmbeddingSearchResult<TextSegment> result = milvusEmbeddingStore.search(builder.build());
            return result.matches().stream()
                    .map(match -> match.embedded().text())
                    .toList();
        } catch (Exception e) {
            log.error("向量检索失败: query={}", query, e);
            return List.of();
        }
    }

    /** 用户记忆检索 */
    public List<String> searchRelevantMemories(String userId, String query, int limit) {
        return searchMemories(query, limit, new IsEqualTo("userId", userId));
    }

    /** 日期范围检索 */
    public List<String> searchMemoriesByDateRange(String userId, String query,
                                                   String startDate, String endDate, int limit) {
        Filter filter = new And(
                new IsEqualTo("userId", userId),
                new And(new IsGreaterThanOrEqualTo("date", startDate),
                        new IsLessThanOrEqualTo("date", endDate))
        );
        return searchMemories(query, limit, filter);
    }

    /**
     * 存到向量库
     */
    public void saveToVectorStore(String userId, String title, String summary) {
        try {
            Embedding embedding = embeddingModel.embed(summary).content();
            
            Metadata metadata = new Metadata();
            metadata.put("userId", userId);
            metadata.put("date", LocalDate.now(ZONE).format(DATE_FORMATTER));
            metadata.put("title", title);
            
            TextSegment textSegment = TextSegment.from(summary, metadata);
            milvusEmbeddingStore.add(embedding, textSegment);
            
            log.debug("用户 {} 的摘要已存入向量数据库，标题：{}", userId, title);
        } catch (Exception e) {
            log.error("用户 {} 的摘要存入向量数据库失败", userId, e);
            throw e;
        }
    }
    
    /**
     * 将 ChatMessage 列表转换为文本
     */
    private String messagesToText(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::messageToText)
                .collect(Collectors.joining("\n"));
    }
    
    /**
     * 将单条 ChatMessage 转换为文本
     */
    private String messageToText(ChatMessage msg) {
        return switch (msg) {
            case UserMessage u -> "哥哥：" + u.singleText();
            case AiMessage a -> "妹妹：" + a.text();
            case SystemMessage s -> "[系统] " + s.text();
            default -> "";
        };
    }
}
