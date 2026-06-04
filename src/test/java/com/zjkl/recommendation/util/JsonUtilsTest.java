package com.zjkl.recommendation.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonUtils 单元测试")
class JsonUtilsTest {

    @Nested
    @DisplayName("stripMarkdownJson()")
    class StripMarkdownJson {

        @Test
        @DisplayName("null 输入应返回 []")
        void nullInputReturnsEmptyArray() {
            assertEquals("[]", JsonUtils.stripMarkdownJson(null));
        }

        @Test
        @DisplayName("空字符串输入应返回空字符串")
        void emptyInputReturnsEmpty() {
            assertEquals("", JsonUtils.stripMarkdownJson(""));
        }

        @Test
        @DisplayName("空白字符串输入应返回空字符串")
        void blankInputReturnsEmpty() {
            assertEquals("", JsonUtils.stripMarkdownJson("   "));
        }

        @Test
        @DisplayName("无 markdown 标记的纯 JSON 应保持不变")
        void plainJsonUnchanged() {
            String json = "{\"key\": \"value\"}";
            assertEquals(json, JsonUtils.stripMarkdownJson(json));
        }

        @Test
        @DisplayName("```json 前缀和 ``` 后缀应被去除")
        void stripJsonMarkdownFence() {
            String input = "```json\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", JsonUtils.stripMarkdownJson(input));
        }

        @Test
        @DisplayName("``` 前缀（无 json）和 ``` 后缀应被去除")
        void stripGenericMarkdownFence() {
            String input = "```\n[1, 2, 3]\n```";
            assertEquals("[1, 2, 3]", JsonUtils.stripMarkdownJson(input));
        }

        @Test
        @DisplayName("仅有 ```json 前缀无后缀时只去除前缀")
        void onlyPrefixWithoutSuffix() {
            String input = "```json\n[1, 2, 3]";
            assertEquals("[1, 2, 3]", JsonUtils.stripMarkdownJson(input));
        }

        @Test
        @DisplayName("仅有 ``` 前缀无后缀时只去除前缀")
        void onlyGenericPrefixWithoutSuffix() {
            String input = "```\n\"hello\"";
            assertEquals("\"hello\"", JsonUtils.stripMarkdownJson(input));
        }

        @Test
        @DisplayName("前后缀去除后多余空白应被 trim")
        void whitespaceAroundFenceIsTrimmed() {
            String input = "  ```json\n  {\"a\":1}  \n```  ";
            assertEquals("{\"a\":1}", JsonUtils.stripMarkdownJson(input));
        }

        @Test
        @DisplayName("```json 优先于 ``` 匹配")
        void jsonFencePreferredOverGeneric() {
            String input = "```json\n{\"x\": 1}\n```";
            assertEquals("{\"x\": 1}", JsonUtils.stripMarkdownJson(input));
        }
    }

    @Nested
    @DisplayName("parseJsonArray()")
    class ParseJsonArray {

        @Test
        @DisplayName("有效 JSON 数组应正确解析")
        void validJsonArray() {
            JsonArray result = JsonUtils.parseJsonArray("[{\"id\": 1}, {\"id\": 2}]");
            assertEquals(2, result.size());
            assertEquals(1, result.get(0).getAsJsonObject().get("id").getAsInt());
        }

        @Test
        @DisplayName("空 JSON 数组应返回空数组")
        void emptyJsonArray() {
            JsonArray result = JsonUtils.parseJsonArray("[]");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无效 JSON 应返回空数组")
        void invalidJsonReturnsEmptyArray() {
            JsonArray result = JsonUtils.parseJsonArray("not json");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("JSON 对象作为输入应返回空数组")
        void jsonObjectReturnsEmptyArray() {
            JsonArray result = JsonUtils.parseJsonArray("{\"key\": \"value\"}");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 输入应返回空数组")
        void nullInputReturnsEmptyArray() {
            JsonArray result = JsonUtils.parseJsonArray(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("带 ```json 标记的数组应被正确解析")
        void markdownFencedArray() {
            JsonArray result = JsonUtils.parseJsonArray("```json\n[{\"id\": 1}]\n```");
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("空白字符串输入应返回空数组")
        void blankInputReturnsEmptyArray() {
            JsonArray result = JsonUtils.parseJsonArray("   ");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("parseJsonObject()")
    class ParseJsonObject {

        @Test
        @DisplayName("有效 JSON 对象应正确解析")
        void validJsonObject() {
            JsonObject result = JsonUtils.parseJsonObject("{\"name\": \"test\", \"value\": 42}");
            assertEquals("test", result.get("name").getAsString());
            assertEquals(42, result.get("value").getAsInt());
        }

        @Test
        @DisplayName("空 JSON 对象应返回空对象")
        void emptyJsonObject() {
            JsonObject result = JsonUtils.parseJsonObject("{}");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无效 JSON 应返回空对象")
        void invalidJsonReturnsEmptyObject() {
            JsonObject result = JsonUtils.parseJsonObject("not json");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("JSON 数组作为输入应返回空对象")
        void jsonArrayReturnsEmptyObject() {
            JsonObject result = JsonUtils.parseJsonObject("[1, 2, 3]");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 输入应返回空对象")
        void nullInputReturnsEmptyObject() {
            JsonObject result = JsonUtils.parseJsonObject(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("带 ```json 标记的对象应被正确解析")
        void markdownFencedObject() {
            JsonObject result = JsonUtils.parseJsonObject("```json\n{\"key\": \"value\"}\n```");
            assertEquals("value", result.get("key").getAsString());
        }

        @Test
        @DisplayName("空白字符串输入应返回空对象")
        void blankInputReturnsEmptyObject() {
            JsonObject result = JsonUtils.parseJsonObject("   ");
            assertTrue(result.isEmpty());
        }
    }
}
