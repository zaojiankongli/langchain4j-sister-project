package com.zjkl.memory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SummaryMemoryService 单元测试 — 覆盖 compressText、情感提取、去重逻辑
 */
class SummaryMemoryServiceTest {

    // ========== compressText ==========

    @Test
    void compressText_shortText_returnsAsIs() {
        String result = SummaryMemoryService.compressText("短文本", 50);
        assertEquals("短文本", result);
    }

    @Test
    void compressText_exactLength_returnsAsIs() {
        String text = "a".repeat(50);
        String result = SummaryMemoryService.compressText(text, 50);
        assertEquals(text, result); // 正好 50 字，不截断
    }

    @Test
    void compressText_longText_breaksAtComma() {
        String text = "今天天气很好，我们去公园散步，看到了很多漂亮的花";
        // maxChars=15 → first 15 chars: "今天天气很好，我们去公园散步，" → 最后一字符是 '，'
        // lastIndexOf('，')=14, 14>7→截断到14→"今天天气很好，我们去公园散步"
        String result = SummaryMemoryService.compressText(text, 15);
        assertEquals("今天天气很好，我们去公园散步", result);
    }

    @Test
    void compressText_longText_breaksAtPeriod() {
        String text = "今天发生了一件事。然后我们去了另一个地方。最后回家了。";
        String result = SummaryMemoryService.compressText(text, 12);
        // 截断到 12 字 → "今天发生了一件事。然后" → 往回找到句号 → "今天发生了一件事"
        assertEquals("今天发生了一件事", result);
    }

    @Test
    void compressText_noBreakFound_hardTruncates() {
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String result = SummaryMemoryService.compressText(text, 10);
        // 没有中文标点 → 硬截断到 10
        assertEquals("ABCDEFGHIJ", result);
    }

    @Test
    void compressText_breakTooEarly_hardTruncates() {
        String text = "A。BCDEFGHIJKLMNOPQRSTUVWXYZ";
        // maxChars=20 → first 20 chars: "A。BCDEFGHIJKLMNOPQRS" (20 chars)
        // 。at index 1, 1 < 10 → 不采用，硬截断
        String result = SummaryMemoryService.compressText(text, 20);
        assertEquals("A。BCDEFGHIJKLMNOPQRS", result);
    }

    @Test
    void compressText_longText_picksLatestBreakPoint() {
        String text = "第一部分内容，第二部分内容。第三部分内容，还有很多内容要写但是被截断了";
        // maxChars=20 → first 20: "第一部分内容，第二部分内容。第三部分内容"
        // '。' at 13, '，' at 6 → Math.max(13,6)=13 → 13>10 → "第一部分内容，第二部分内容"
        String result = SummaryMemoryService.compressText(text, 20);
        assertEquals("第一部分内容，第二部分内容", result);
    }

    // ========== compressText 参数化 ==========

    @ParameterizedTest
    @CsvSource({
            "短, 50, 短",
            "一二三四五六七八九十一二三四五六七八九十, 10, 一二三四五六七八九十",
            "聊了摄影。还聊了旅行, 7, 聊了摄影",
    })
    void compressText_parameterized(String input, int maxChars, String expected) {
        assertEquals(expected, SummaryMemoryService.compressText(input, maxChars));
    }

    // ========== compressText 边界 ==========

    @Test
    void compressText_doubleByteChars_handlesCorrectly() {
        // CJK 字符在 Java String 中每个占 1 个 char（基本多文种平面内）
        String text = "你好世界！这是一个测试。更多内容";
        // maxChars=10 → first 10 chars: "你好世界！这是一个测" → 无 '。' '，' → 硬截
        String result = SummaryMemoryService.compressText(text, 10);
        assertEquals("你好世界！这是一个测", result);
    }

    @Test
    void compressText_emojiAndMixed_handlesCorrectly() {
        // Emoji 可能占两个 char，测试鲁棒性
        String text = "😀今天很开心，聊了很多有趣的事情";
        String result = SummaryMemoryService.compressText(text, 12);
        // 截断到 12 char → 往回找 '，' → "😀今天很开心"
        assertNotNull(result);
        assertTrue(result.length() <= 12);
    }

    // ========== 空值/边界 ==========

    @Test
    void compressText_emptyString_returnsEmpty() {
        assertEquals("", SummaryMemoryService.compressText("", 50));
    }

    @Test
    void compressText_null_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> SummaryMemoryService.compressText(null, 50));
    }

    @Test
    void compressText_zeroMaxChars_returnsEmpty() {
        assertEquals("", SummaryMemoryService.compressText("hello", 0));
    }
}
