package com.zjkl.recommendation.assistant;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecommendAccumulator 单元测试")
class RecommendAccumulatorTest {

    private static final Gson GSON = new Gson();

    private RecommendAccumulator accumulator;

    @BeforeEach
    void setUp() {
        accumulator = new RecommendAccumulator();
    }

    @Nested
    @DisplayName("accumulate() - 基础场景")
    class AccumulateBasic {

        @Test
        @DisplayName("空已有结果 + 有效待评分结果 → 应添加达标项")
        void emptyExistingWithValidScoredResult_addsQualifyingItem() {
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/doc1\", \"relevanceScore\": 0.85, \"title\": \"Doc1\"}]}";
            String existing = "[]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
            JsonObject item = arr.get(0).getAsJsonObject();
            assertEquals("https://example.com/doc1", item.get("url").getAsString());
            assertEquals("agentic", item.get("source").getAsString());
        }

        @Test
        @DisplayName("已有结果 + 重复 URL → 不应重复添加")
        void existingWithDuplicateUrl_doesNotDuplicate() {
            String existing = "[{\"url\": \"https://example.com/doc1\", \"relevanceScore\": 0.9}]";
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/doc1\", \"relevanceScore\": 0.85}]}";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("低于阈值的分数不应被添加")
        void belowThresholdScore_notAdded() {
            String existing = "[]";
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/low\", \"relevanceScore\": 0.3}]}";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertTrue(arr.isEmpty());
        }

        @Test
        @DisplayName("刚好等于阈值的分数应被添加")
        void exactlyThresholdScore_added() {
            String existing = "[]";
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/edge\", \"relevanceScore\": 0.6}]}";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("混合分数：仅高于阈值且非重复的项应被添加")
        void mixedScores_onlyQualifyingAdded() {
            String existing = "[{\"url\": \"https://example.com/existing\", \"relevanceScore\": 0.95}]";
            String scoredResult = "{\"recommendations\": ["
                    + "{\"url\": \"https://example.com/new1\", \"relevanceScore\": 0.8},"
                    + "{\"url\": \"https://example.com/low1\", \"relevanceScore\": 0.2},"
                    + "{\"url\": \"https://example.com/existing\", \"relevanceScore\": 0.7},"
                    + "{\"url\": \"https://example.com/new2\", \"relevanceScore\": 0.9}"
                    + "]}";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(3, arr.size());
            assertTrue(arrAsString(arr).contains("https://example.com/existing"));
            assertTrue(arrAsString(arr).contains("https://example.com/new1"));
            assertTrue(arrAsString(arr).contains("https://example.com/new2"));
            assertFalse(arrAsString(arr).contains("https://example.com/low1"));
        }
    }

    @Nested
    @DisplayName("accumulate() - 边界和异常场景")
    class AccumulateEdgeCases {

        @Test
        @DisplayName("scoredResult 没有 recommendations 字段 → 已有结果不变")
        void scoredResultMissingRecommendationsField() {
            String scoredResult = "{\"otherField\": \"value\"}";
            String existing = "[{\"url\": \"https://example.com/existing\", \"relevanceScore\": 0.9}]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("scoredResult 的 recommendations 不是数组 → 已有结果不变")
        void scoredResultRecommendationsNotArray() {
            String scoredResult = "{\"recommendations\": \"not an array\"}";
            String existing = "[{\"url\": \"https://example.com/existing\", \"relevanceScore\": 0.9}]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("scoredResult 为空对象 → 已有结果不变")
        void scoredResultEmptyObject() {
            String scoredResult = "{}";
            String existing = "[{\"url\": \"https://example.com/doc\", \"relevanceScore\": 0.8}]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("单个项缺少 url 字段 → 该项被跳过")
        void itemMissingUrl_skipped() {
            String scoredResult = "{\"recommendations\": [{\"relevanceScore\": 0.8}]}";
            String existing = "[]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertTrue(arr.isEmpty());
        }

        @Test
        @DisplayName("单个项缺少 relevanceScore 字段 → 该项被跳过")
        void itemMissingScore_skipped() {
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/doc\"}]}";
            String existing = "[]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertTrue(arr.isEmpty());
        }

        @Test
        @DisplayName("已有结果中有无 url 的项 → 不应导致异常")
        void existingItemWithoutUrl_doesNotThrow() {
            String existing = "[{\"title\": \"no url item\"}, {\"url\": \"https://example.com/valid\", \"relevanceScore\": 0.9}]";
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/new\", \"relevanceScore\": 0.7}]}";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(3, arr.size());
        }

        @Test
        @DisplayName("scoredResult 为无效 JSON → 已有结果不变")
        void scoredResultInvalidJson() {
            String existing = "[{\"url\": \"https://example.com/doc\", \"relevanceScore\": 0.8}]";
            String result = accumulator.accumulate("not valid json", existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("existing 为无效 JSON → 返回新建空数组的结果")
        void existingInvalidJson() {
            String scoredResult = "{\"recommendations\": [{\"url\": \"https://example.com/new\", \"relevanceScore\": 0.8}]}";
            String result = accumulator.accumulate(scoredResult, "not valid json");
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(1, arr.size());
        }

        @Test
        @DisplayName("多个达标项应全部添加并带有 agentic 来源标识")
        void multipleQualifyingItems_allAddedWithSource() {
            String scoredResult = "{\"recommendations\": ["
                    + "{\"url\": \"https://example.com/a\", \"relevanceScore\": 0.9},"
                    + "{\"url\": \"https://example.com/b\", \"relevanceScore\": 0.75},"
                    + "{\"url\": \"https://example.com/c\", \"relevanceScore\": 0.88}"
                    + "]}";
            String existing = "[]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertEquals(3, arr.size());
            for (int i = 0; i < arr.size(); i++) {
                assertEquals("agentic", arr.get(i).getAsJsonObject().get("source").getAsString());
            }
        }

        @Test
        @DisplayName("全部低于阈值的项 → 不添加任何项")
        void allBelowThreshold_noneAdded() {
            String scoredResult = "{\"recommendations\": ["
                    + "{\"url\": \"https://example.com/a\", \"relevanceScore\": 0.1},"
                    + "{\"url\": \"https://example.com/b\", \"relevanceScore\": 0.5}"
                    + "]}";
            String existing = "[]";

            String result = accumulator.accumulate(scoredResult, existing);
            JsonArray arr = GSON.fromJson(result, JsonArray.class);

            assertTrue(arr.isEmpty());
        }
    }

    private static String arrAsString(JsonArray arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            if (obj.has("url")) {
                sb.append(obj.get("url").getAsString());
            }
        }
        return sb.toString();
    }
}
