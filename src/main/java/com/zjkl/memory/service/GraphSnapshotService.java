package com.zjkl.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.MilvusProperties;
import com.zjkl.common.util.MilvusQueryUtil;
import com.zjkl.memory.constant.GraphRedisKeys;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GraphSnapshotService {

    private static final long SNAPSHOT_REBUILD_GAP_MS = 60 * 60 * 1000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final MilvusClientV2 milvusClientV2;
    private final MilvusProperties milvusProperties;
    private final PromptTemplateService promptTemplateService;
    private final QwenChatModel qwenChatModel;
    private final Executor asyncExecutor;

    public GraphSnapshotService(StringRedisTemplate stringRedisTemplate,
                                MilvusClientV2 milvusClientV2,
                                MilvusProperties milvusProperties,
                                PromptTemplateService promptTemplateService,
                                QwenChatModel qwenChatModel,
                                Executor asyncExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.milvusClientV2 = milvusClientV2;
        this.milvusProperties = milvusProperties;
        this.promptTemplateService = promptTemplateService;
        this.qwenChatModel = qwenChatModel;
        this.asyncExecutor = asyncExecutor;
    }

    public String getSnapshot(String userId) {
        // 使用 Redis Pipeline 一次性获取 3 个 key，减少 RTT 和竞态窗口
        String snapshotKey = GraphRedisKeys.SNAPSHOT_KEY + userId;
        String writeBatchKey = GraphRedisKeys.LAST_WRITE_BATCH_KEY + userId;
        String versionKey = GraphRedisKeys.SNAPSHOT_VERSION_KEY + userId;

        List<Object> pipelineResults = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    byte[] sk = snapshotKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] wbk = writeBatchKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] vk = versionKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    connection.stringCommands().get(sk);
                    connection.stringCommands().get(wbk);
                    connection.stringCommands().get(vk);
                    return null;
                }
        );

        String snapshot = pipelineResults.size() > 0 ? (String) pipelineResults.get(0) : null;
        String currentVersion = pipelineResults.size() > 1 ? (String) pipelineResults.get(1) : null;
        String snapshotVersion = pipelineResults.size() > 2 ? (String) pipelineResults.get(2) : null;

        if ((snapshot == null || snapshot.isBlank()) && currentVersion != null && !currentVersion.isBlank()) {
            maybeRebuildAsync(userId, currentVersion);
            return "";
        }
        if (!Objects.equals(currentVersion, snapshotVersion)) {
            maybeRebuildAsync(userId, currentVersion);
        }
        return snapshot == null ? "" : snapshot;
    }

    private void maybeRebuildAsync(String userId, String targetVersion) {
        // 使用 Redis SETNX 分布式锁防止并发请求重复触发重建
        String lockKey = GraphRedisKeys.LAST_REBUILD_AT_KEY + "lock:" + userId;
        String lockToken = java.util.UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, lockToken, java.time.Duration.ofMillis(SNAPSHOT_REBUILD_GAP_MS));
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("图 snapshot 重建锁未获取，跳过: userId={}", userId);
            return;
        }
        // 使用注入的 executor 异步执行（@Async 自调用无效，改用 CompletableFuture）
        CompletableFuture.runAsync(() -> {
            try {
                rebuildSnapshot(userId, targetVersion);
            } finally {
                // 只删除自己持有的锁，避免误删其他实例的锁
                String current = stringRedisTemplate.opsForValue().get(lockKey);
                if (lockToken.equals(current)) {
                    stringRedisTemplate.delete(lockKey);
                }
            }
        }, asyncExecutor)
                .exceptionally(e -> {
                    log.warn("图 snapshot 异步重建调度失败 userId={}", userId, e);
                    String current = stringRedisTemplate.opsForValue().get(lockKey);
                    if (lockToken.equals(current)) {
                        stringRedisTemplate.delete(lockKey);
                    }
                    return null;
                });
    }

    /**
     * 实际执行重建（在异步线程中运行）
     * 成功后才写入速率限制时间戳，失败时不阻止后续重试
     */
    private void rebuildSnapshot(String userId, String targetVersion) {
        try {
            String graphContext = buildGraphContext(userId);
            if (graphContext.isBlank()) {
                log.debug("图 snapshot 跳过：无图上下文 userId={}", userId);
                return;
            }
            String prompt = promptTemplateService.render("graph-snapshot", Map.of("graph_context", graphContext));
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是一个关系上下文压缩器。只输出纯文本。"),
                            UserMessage.from(prompt)
                    )
                    .build());
            String snapshot = response.aiMessage() != null ? response.aiMessage().text() : "";
            if (snapshot == null || snapshot.isBlank()) {
                log.debug("图 snapshot 跳过：LLM 返回空 userId={}", userId);
                return;
            }
            stringRedisTemplate.opsForValue().set(GraphRedisKeys.SNAPSHOT_KEY + userId,
                    snapshot, GraphRedisKeys.SNAPSHOT_TTL);
            stringRedisTemplate.opsForValue().set(GraphRedisKeys.SNAPSHOT_VERSION_KEY + userId,
                    targetVersion == null ? "" : targetVersion, GraphRedisKeys.SNAPSHOT_TTL);
            // 成功后才写入速率限制时间戳（失败时不写，允许下次重试）
            stringRedisTemplate.opsForValue().set(GraphRedisKeys.LAST_REBUILD_AT_KEY + userId,
                    String.valueOf(System.currentTimeMillis()), GraphRedisKeys.SNAPSHOT_TTL);
            log.debug("图 snapshot 已重建 userId={}, version={}", userId, targetVersion);
        } catch (Exception e) {
            log.warn("图 snapshot 重建失败 userId={}", userId, e);
        }
    }

    private String buildGraphContext(String userId) {
        List<Map<String, Object>> entities = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphEntityCollectionName(),
                MilvusQueryUtil.userFilter(userId),
                List.of("text", "type", "mention_count", "last_seen")
        );
        List<Map<String, Object>> relations = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphRelationCollectionName(),
                MilvusQueryUtil.userFilter(userId),
                List.of("text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp")
        );

        List<String> topEntities = entities.stream()
                .sorted(Comparator.<Map<String, Object>>comparingLong(row -> parseLong(row.get("mention_count"))).reversed()
                        .thenComparing((a, b) -> Long.compare(parseLong(b.get("last_seen")), parseLong(a.get("last_seen")))))
                .limit(10)
                .map(row -> row.get("text") + "(" + row.get("type") + ")")
                .toList();

        List<String> topRelations = relations.stream()
                .sorted(Comparator.<Map<String, Object>>comparingLong(row -> parseLong(row.get("timestamp"))).reversed())
                .limit(20)
                .map(row -> row.get("text").toString())
                .toList();

        if (topEntities.isEmpty() && topRelations.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (!topEntities.isEmpty()) {
            builder.append("实体：\n");
            topEntities.forEach(entity -> builder.append("- ").append(entity).append("\n"));
        }
        if (!topRelations.isEmpty()) {
            builder.append("关系：\n");
            topRelations.forEach(relation -> builder.append("- ").append(relation).append("\n"));
        }
        return builder.toString().trim();
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
