package com.zjkl.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.MilvusProperties;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.response.QueryResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphEntityServiceTest {

    @Mock private MilvusClientV2 milvusClientV2;
    @Mock private MilvusProperties milvusProperties;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private QwenChatModel qwenChatModel;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    private GraphEntityService service;

    @BeforeEach
    void setUp() {
        service = new GraphEntityService(
                milvusClientV2,
                milvusProperties,
                embeddingModel,
                qwenChatModel,
                promptTemplateService,
                stringRedisTemplate,
                objectMapper
        );
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(milvusProperties.getDatabase()).thenReturn("default");
        lenient().when(milvusProperties.getGraphEntityCollectionName()).thenReturn("graph_entities");
        lenient().when(milvusProperties.getGraphRelationCollectionName()).thenReturn("graph_relations");
        lenient().when(milvusProperties.getGraphPassageCollectionName()).thenReturn("graph_passages");
        lenient().doNothing().when(milvusClientV2).flush(any());
    }

    @Test
    void weeklyCompactGraph_shouldSkipUserWhenDistributedCompactionLockAlreadyExists() {
        when(setOperations.members("graph:knownUsers")).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("graph:compact:u1"),
                eq("1"),
                eq(Duration.ofHours(6))
        )).thenReturn(false);

        service.weeklyCompactGraph();

        verify(milvusClientV2, never()).query(any());
    }

    @Test
    void weeklyCompactGraph_shouldReleaseDistributedCompactionLockWhenCompactionFails() {
        when(milvusProperties.getGraphEntityCollectionName()).thenReturn("entities");
        when(setOperations.members("graph:knownUsers")).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("graph:compact:u1"),
                eq("1"),
                eq(Duration.ofHours(6))
        )).thenReturn(true);
        doThrow(new RuntimeException("query failed")).when(milvusClientV2).query(any());

        service.weeklyCompactGraph();

        verify(stringRedisTemplate).delete("graph:compact:u1");
    }
}
