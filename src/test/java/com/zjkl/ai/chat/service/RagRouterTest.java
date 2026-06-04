package com.zjkl.ai.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagRouter 单元测试 — 覆盖 RouterResult 结构
 */
class RagRouterTest {

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
