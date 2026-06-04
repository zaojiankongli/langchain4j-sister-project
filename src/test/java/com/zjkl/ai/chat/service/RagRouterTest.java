package com.zjkl.ai.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagRouter 单元测试 — 覆盖 parseNeedSearch（向后兼容） + RouterResult 结构
 */
class RagRouterTest {

    // ========== parseNeedSearch — camelCase（langchain4j AiServices 字段名） ==========

    @Test
    void parseNeedSearch_plainTrue_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch("{\"needSearch\": true}"));
    }

    @Test
    void parseNeedSearch_plainFalse_returnsFalse() {
        assertFalse(RagRouter.parseNeedSearch("{\"needSearch\": false}"));
    }

    @Test
    void parseNeedSearch_capitalTrue_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch("{\"needSearch\": True}"));
    }

    @Test
    void parseNeedSearch_allCaps_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch("{\"needSearch\": TRUE}"));
    }

    @Test
    void parseNeedSearch_withSpaces_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch("{ \"needSearch\" : true }"));
    }

    @Test
    void parseNeedSearch_inJsonFence_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch("```json\n{\"needSearch\": true}\n```"));
    }

    @Test
    void parseNeedSearch_inPlainFence_returnsFalse() {
        assertFalse(RagRouter.parseNeedSearch("```\n{\"needSearch\": false}\n```"));
    }

    @Test
    void parseNeedSearch_withSurroundingText_returnsTrue() {
        assertTrue(RagRouter.parseNeedSearch(
                "Based on the context:\n{\"needSearch\": true}"));
    }

    @Test
    void parseNeedSearch_null_returnsFalse() {
        assertFalse(RagRouter.parseNeedSearch(null));
    }

    @Test
    void parseNeedSearch_emptyString_returnsFalse() {
        assertFalse(RagRouter.parseNeedSearch(""));
    }

    @Test
    void parseNeedSearch_randomText_returnsFalse() {
        assertFalse(RagRouter.parseNeedSearch("hello world"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"needSearch\": True}",
            "  {\"needSearch\":true}  ",
            "{\"needSearch\":true,\"other\":\"value\"}",
            "some text {\"needSearch\": true} more text"
    })
    void parseNeedSearch_variations_returnsTrue(String input) {
        assertTrue(RagRouter.parseNeedSearch(input),
                "Expected true for: " + input);
    }

    // ========== RouterResult 结构 ==========

    @Test
    void routerResult_noSearch_allFieldsFalse() {
        RouterResult r = RouterResult.noSearch();
        assertFalse(r.needSearch());
        assertFalse(r.needMemorySearch());
        assertFalse(r.needGraphSearch());
        assertNull(r.primarySource());
        assertNull(r.dateHint());
        assertNull(r.topicHint());
        assertNull(r.sentimentHint());
        assertFalse(r.hasFilters());
    }

    @Test
    void routerResult_withFilters_convertsCorrectly() {
        RouterResult r = new RouterResult(true, false, "memory", "最近一周", "摄影", "开心的");
        assertTrue(r.needSearch());
        assertTrue(r.needMemorySearch());
        assertFalse(r.needGraphSearch());
        assertTrue(r.hasFilters());

        MemorySearchFilters f = r.toFilters();
        assertEquals("最近一周", f.dateHint());
        assertEquals("摄影", f.topicHint());
        assertEquals("开心的", f.sentimentHint());
    }

    @Test
    void routerResult_partialFilters_someNull() {
        RouterResult r = new RouterResult(true, false, "memory", "上个月", null, null);
        assertTrue(r.needSearch());
        assertTrue(r.hasFilters());

        MemorySearchFilters f = r.toFilters();
        assertEquals("上个月", f.dateHint());
        assertNull(f.topicHint());
        assertNull(f.sentimentHint());
    }

    @Test
    void routerResult_noFilters_butNeedSearch() {
        RouterResult r = new RouterResult(false, true, "graph", null, null, null);
        assertTrue(r.needSearch());
        assertFalse(r.hasFilters());
        assertFalse(r.needMemorySearch());
        assertTrue(r.needGraphSearch());

        MemorySearchFilters f = r.toFilters();
        assertTrue(f.isEmpty());
    }

    @Test
    void routerResult_toFilters_emptyWhenNull() {
        RouterResult r = RouterResult.noSearch();
        MemorySearchFilters f = r.toFilters();
        assertTrue(f.isEmpty());
        assertFalse(r.needSearch());
    }
}
