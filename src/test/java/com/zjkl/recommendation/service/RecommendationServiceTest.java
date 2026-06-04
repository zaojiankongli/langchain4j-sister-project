package com.zjkl.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.mapper.UserRecommendationMapper;
import dev.langchain4j.agentic.UntypedAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService 单元测试")
class RecommendationServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private UntypedAgent recommendationWorkflow;

    @Mock
    private UserRecommendationMapper recommendationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new RecommendationService(recommendationWorkflow, recommendationMapper, redisTemplate);
        ReflectionTestUtils.setField(service, "self", service);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("同用户生成锁已存在时不应重复启动工作流")
    void generateRecommendations_shouldReturnExistingWhenGenerationLockExists() {
        String userId = "u1";
        List<UserRecommendation> existing = List.of(createRec("https://existing.com", 0.8));

        when(recommendationMapper.selectByUserIdAndDate(eq(userId), any())).thenReturn(List.of(), existing);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        List<UserRecommendation> result = service.generateRecommendations(userId);

        assertEquals(1, result.size());
        assertEquals("https://existing.com", result.get(0).getUrl());
        verify(recommendationWorkflow, never()).invoke(any());
    }

    @Test
    @DisplayName("工作流失败时应释放生成锁")
    void generateRecommendations_shouldReleaseGenerationLockWhenWorkflowFails() {
        String userId = "u1";

        when(recommendationMapper.selectByUserIdAndDate(eq(userId), any())).thenReturn(List.of());
        when(recommendationWorkflow.invoke(any())).thenThrow(new RuntimeException("workflow failed"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.generateRecommendations(userId));

        assertTrue(error.getMessage().contains("推荐工作流执行失败"));
        verify(redisTemplate).delete("recommendation:generate:" + java.time.LocalDate.now() + ":" + userId);
    }

    @Nested
    @DisplayName("parseRecommendations()")
    class ParseRecommendations {

        @Test
        @DisplayName("有效 JSON 数组应解析为 UserRecommendation 列表（含 imageUrl）")
        void validJsonArray_parsesToList() throws Exception {
            String json = "[{\"title\": \"Test Doc\", \"url\": \"https://example.com/doc\", \"imageUrl\": \"https://example.com/img.jpg\", \"description\": \"A test\", \"relevanceScore\": 0.85, \"resourceType\": \"article\", \"source\": \"firecrawl\"}]";

            List<UserRecommendation> result = invokeParseRecommendations(json);

            assertEquals(1, result.size());
            UserRecommendation rec = result.get(0);
            assertEquals("Test Doc", rec.getTitle());
            assertEquals("https://example.com/doc", rec.getUrl());
            assertEquals("https://example.com/img.jpg", rec.getImageUrl());
            assertEquals("A test", rec.getDescription());
            assertEquals(0, BigDecimal.valueOf(0.85).compareTo(rec.getRelevanceScore()));
            assertEquals("article", rec.getResourceType());
            assertEquals("firecrawl", rec.getSource());
        }

        @Test
        @DisplayName("空数组应返回空列表")
        void emptyArray_returnsEmptyList() throws Exception {
            List<UserRecommendation> result = invokeParseRecommendations("[]");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 输入应返回空列表")
        void nullInput_returnsEmptyList() throws Exception {
            List<UserRecommendation> result = invokeParseRecommendations(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空白字符串输入应返回空列表")
        void blankInput_returnsEmptyList() throws Exception {
            List<UserRecommendation> result = invokeParseRecommendations("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无效 JSON 应返回空列表")
        void invalidJson_returnsEmptyList() throws Exception {
            List<UserRecommendation> result = invokeParseRecommendations("not json");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("缺少 URL 的项应被跳过")
        void itemWithoutUrl_skipped() throws Exception {
            String json = "[{\"title\": \"No URL\"}, {\"title\": \"With URL\", \"url\": \"https://example.com/valid\"}]";
            List<UserRecommendation> result = invokeParseRecommendations(json);
            assertEquals(1, result.size());
            assertEquals("With URL", result.get(0).getTitle());
        }

        @Test
        @DisplayName("URL 为空的项应被跳过")
        void itemWithBlankUrl_skipped() throws Exception {
            String json = "[{\"title\": \"Blank URL\", \"url\": \" \"}, {\"title\": \"Valid\", \"url\": \"https://example.com/doc\"}]";
            List<UserRecommendation> result = invokeParseRecommendations(json);
            assertEquals(1, result.size());
            assertEquals("Valid", result.get(0).getTitle());
        }

        @Test
        @DisplayName("缺少可选字段应使用默认值（含 imageUrl）")
        void missingOptionalFields_usesDefaults() throws Exception {
            String json = "[{\"url\": \"https://example.com/doc\"}]";
            List<UserRecommendation> result = invokeParseRecommendations(json);
            assertEquals(1, result.size());
            UserRecommendation rec = result.get(0);
            assertEquals("推荐资源", rec.getTitle());
            assertEquals("", rec.getImageUrl());
            assertEquals("", rec.getDescription());
            assertEquals("agentic", rec.getSource());
            assertEquals("document", rec.getResourceType());
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(rec.getRelevanceScore()));
        }

        @Test
        @DisplayName("多个项应全部解析")
        void multipleItems_allParsed() throws Exception {
            String json = "["
                    + "{\"url\": \"https://example.com/1\", \"title\": \"One\"},"
                    + "{\"url\": \"https://example.com/2\", \"title\": \"Two\"},"
                    + "{\"url\": \"https://example.com/3\", \"title\": \"Three\"}"
                    + "]";
            List<UserRecommendation> result = invokeParseRecommendations(json);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("relevanceScore 为 null 时应使用默认值")
        void nullRelevanceScore_usesDefault() throws Exception {
            String json = "[{\"url\": \"https://example.com/doc\", \"relevanceScore\": null}]";
            List<UserRecommendation> result = invokeParseRecommendations(json);
            assertEquals(1, result.size());
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(result.get(0).getRelevanceScore()));
        }
    }

    @Nested
    @DisplayName("sortByRelevanceScore()")
    class SortByRelevanceScore {

        @Test
        @DisplayName("应按分数降序排列")
        void sortsDescendingByScore() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            list.add(createRec("https://a.com", 0.3));
            list.add(createRec("https://b.com", 0.9));
            list.add(createRec("https://c.com", 0.6));

            invokeSortByRelevanceScore(list);

            assertEquals(0.9, list.get(0).getRelevanceScore().doubleValue());
            assertEquals(0.6, list.get(1).getRelevanceScore().doubleValue());
            assertEquals(0.3, list.get(2).getRelevanceScore().doubleValue());
        }

        @Test
        @DisplayName("null 分数不应压过有效分数")
        void nullScoresLast() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            list.add(createRec("https://a.com", null));
            list.add(createRec("https://b.com", 0.5));
            list.add(createRec("https://c.com", 0.8));

            invokeSortByRelevanceScore(list);

            assertEquals(0.8, list.get(0).getRelevanceScore().doubleValue());
            assertEquals(0.5, list.get(1).getRelevanceScore().doubleValue());
            assertNull(list.get(2).getRelevanceScore());
        }

        @Test
        @DisplayName("空列表不应抛出异常")
        void emptyList_doesNotThrow() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            invokeSortByRelevanceScore(list);
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("分数相同时顺序应保持不变")
        void equalScores_maintainsOrder() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            UserRecommendation a = createRec("https://a.com", 0.5);
            a.setTitle("A");
            UserRecommendation b = createRec("https://b.com", 0.5);
            b.setTitle("B");
            list.add(a);
            list.add(b);

            invokeSortByRelevanceScore(list);

            assertEquals("A", list.get(0).getTitle());
            assertEquals("B", list.get(1).getTitle());
        }
    }

    @Nested
    @DisplayName("truncateToTopN()")
    class TruncateToTopN {

        @Test
        @DisplayName("列表长度小于 N 应保持不变")
        void listSmallerThanN_unchanged() throws Exception {
            List<UserRecommendation> list = Arrays.asList(createRec("https://a.com", 1.0));
            List<UserRecommendation> result = invokeTruncateToTopN(list, 5);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("列表长度等于 N 应保持不变")
        void listEqualsN_unchanged() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                list.add(createRec("https://" + i + ".com", 1.0));
            }
            List<UserRecommendation> result = invokeTruncateToTopN(list, 15);
            assertEquals(15, result.size());
        }

        @Test
        @DisplayName("列表长度大于 N 应截断为前 N 个")
        void listLargerThanN_truncated() throws Exception {
            List<UserRecommendation> list = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                list.add(createRec("https://" + i + ".com", 1.0));
            }
            List<UserRecommendation> result = invokeTruncateToTopN(list, 10);
            assertEquals(10, result.size());
            assertEquals("https://0.com", result.get(0).getUrl());
        }

        @Test
        @DisplayName("空列表应返回空列表")
        void emptyList_returnsEmpty() throws Exception {
            List<UserRecommendation> result = invokeTruncateToTopN(new ArrayList<>(), 15);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("N 为 0 应返回空列表")
        void topNZero_returnsEmpty() throws Exception {
            List<UserRecommendation> list = Arrays.asList(createRec("https://a.com", 1.0));
            List<UserRecommendation> result = invokeTruncateToTopN(list, 0);
            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("inferResourceType()")
    class InferResourceType {

        @Test
        @DisplayName("JSON 中指定了 resourceType 应返回该类型")
        void explicitTypeInJson() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("resourceType", "video");
            assertEquals("video", invokeInferResourceType(obj, "https://example.com/doc"));
        }

        @Test
        @DisplayName("URL 包含 youtube 应推断为 video")
        void urlContainsYoutube() throws Exception {
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://youtube.com/watch?v=abc"));
        }

        @Test
        @DisplayName("URL 包含 bilibili 应推断为 video")
        void urlContainsBilibili() throws Exception {
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://bilibili.com/video/BV123"));
        }

        @Test
        @DisplayName("URL 包含 vimeo 应推断为 video")
        void urlContainsVimeo() throws Exception {
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://vimeo.com/123456"));
        }

        @Test
        @DisplayName("URL 包含 blog 应推断为 article")
        void urlContainsBlog() throws Exception {
            assertEquals("article", invokeInferResourceType(MAPPER.createObjectNode(), "https://example.com/blog/post-1"));
        }

        @Test
        @DisplayName("URL 包含 article 应推断为 article")
        void urlContainsArticle() throws Exception {
            assertEquals("article", invokeInferResourceType(MAPPER.createObjectNode(), "https://example.com/articles/123"));
        }

        @Test
        @DisplayName("URL 包含 medium 应推断为 article")
        void urlContainsMedium() throws Exception {
            assertEquals("article", invokeInferResourceType(MAPPER.createObjectNode(), "https://medium.com/@user/post"));
        }

        @Test
        @DisplayName("无匹配的 URL 应返回 document")
        void noMatch_returnsDocument() throws Exception {
            assertEquals("document", invokeInferResourceType(MAPPER.createObjectNode(), "https://example.com/page"));
        }

        @Test
        @DisplayName("同时匹配 video 和 article 关键字时 video 优先")
        void videoMatchTakesPriority() throws Exception {
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://medium.com/youtube-video"));
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://blog.example.com/bilibili"));
            assertEquals("video", invokeInferResourceType(MAPPER.createObjectNode(), "https://article.example.com/vimeo"));
        }

        @Test
        @DisplayName("空类型和空 URL 应返回 document")
        void emptyTypeAndUrl_returnsDocument() throws Exception {
            assertEquals("document", invokeInferResourceType(MAPPER.createObjectNode(), ""));
        }
    }

    @Nested
    @DisplayName("parseRelevanceScore()")
    class ParseRelevanceScore {

        @Test
        @DisplayName("有效分数应正确解析为 BigDecimal")
        void validScore_parsesCorrectly() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("relevanceScore", 0.75);
            BigDecimal result = invokeParseRelevanceScore(obj);
            assertEquals(0, BigDecimal.valueOf(0.75).compareTo(result));
        }

        @Test
        @DisplayName("缺少 relevanceScore 应返回默认值 0.5")
        void missingScore_returnsDefault() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            BigDecimal result = invokeParseRelevanceScore(obj);
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(result));
        }

        @Test
        @DisplayName("relevanceScore 为 null 应返回默认值 0.5")
        void nullScore_returnsDefault() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.putNull("relevanceScore");
            BigDecimal result = invokeParseRelevanceScore(obj);
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(result));
        }

        @Test
        @DisplayName("relevanceScore 为 0 应返回 BigDecimal.ZERO")
        void zeroScore_returnsZero() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("relevanceScore", 0);
            BigDecimal result = invokeParseRelevanceScore(obj);
            assertEquals(0, BigDecimal.ZERO.compareTo(result));
        }

        @Test
        @DisplayName("relevanceScore 为 1.0 应正确解析")
        void maxScore_parsesCorrectly() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("relevanceScore", 1.0);
            BigDecimal result = invokeParseRelevanceScore(obj);
            assertEquals(0, BigDecimal.valueOf(1.0).compareTo(result));
        }
    }

    @Nested
    @DisplayName("getJsonString()")
    class GetJsonString {

        @Test
        @DisplayName("存在的键应返回对应值")
        void existingKey_returnsValue() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("name", "test-value");
            String result = invokeGetJsonString(obj, "name", "default");
            assertEquals("test-value", result);
        }

        @Test
        @DisplayName("不存在的键应返回默认值")
        void missingKey_returnsDefault() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            String result = invokeGetJsonString(obj, "missing", "default");
            assertEquals("default", result);
        }

        @Test
        @DisplayName("值为 null 的键应返回默认值")
        void nullValue_returnsDefault() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.putNull("nullable");
            String result = invokeGetJsonString(obj, "nullable", "default");
            assertEquals("default", result);
        }

        @Test
        @DisplayName("空字符串值应返回空字符串")
        void emptyStringValue() throws Exception {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("empty", "");
            String result = invokeGetJsonString(obj, "empty", "default");
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("batchInsertRecommendations()")
    class BatchInsertRecommendations {

        @Test
        @DisplayName("应设置 userId、日期、点击状态并调用 mapper")
        void setsFieldsAndCallsMapper() {
            List<UserRecommendation> recs = new ArrayList<>();
            recs.add(createRec("https://example.com/doc", 0.85));

            service.batchInsertRecommendations("user-123", recs);

            UserRecommendation rec = recs.get(0);
            assertEquals("user-123", rec.getUserId());
            assertNotNull(rec.getRecommendationDate());
            assertFalse(rec.isClicked());
            verify(recommendationMapper).batchInsert(anyList());
        }
    }

    // ========== 反射辅助方法 ==========

    @SuppressWarnings("unchecked")
    private List<UserRecommendation> invokeParseRecommendations(String json) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("parseRecommendations", String.class);
        method.setAccessible(true);
        return (List<UserRecommendation>) method.invoke(service, json);
    }

    private void invokeSortByRelevanceScore(List<UserRecommendation> list) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("sortByRelevanceScore", List.class);
        method.setAccessible(true);
        method.invoke(service, list);
    }

    @SuppressWarnings("unchecked")
    private List<UserRecommendation> invokeTruncateToTopN(List<UserRecommendation> list, int topN) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("truncateToTopN", List.class, int.class);
        method.setAccessible(true);
        return (List<UserRecommendation>) method.invoke(service, list, topN);
    }

    private String invokeInferResourceType(JsonNode obj, String url) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("inferResourceType", JsonNode.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, obj, url);
    }

    private BigDecimal invokeParseRelevanceScore(JsonNode obj) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("parseRelevanceScore", JsonNode.class);
        method.setAccessible(true);
        return (BigDecimal) method.invoke(service, obj);
    }

    private String invokeGetJsonString(JsonNode obj, String key, String defaultValue) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod("getJsonString", JsonNode.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, obj, key, defaultValue);
    }

    private static UserRecommendation createRec(String url, Double score) {
        UserRecommendation rec = new UserRecommendation();
        rec.setUrl(url);
        if (score != null) {
            rec.setRelevanceScore(BigDecimal.valueOf(score));
        }
        return rec;
    }
}
