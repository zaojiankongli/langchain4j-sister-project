package com.zjkl.common.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Slf4j
public class DateFilterParser {

    private DateFilterParser() {}

    public static String[] parse(String filter) {
        if (filter == null || filter.isBlank() || "最近".equals(filter)) {
            return new String[]{null, null};
        }

        int currentYear = LocalDate.now().getYear();

        // "当前年" → 当前年范围
        if (filter.equals(currentYear + "年")) {
            return new String[]{currentYear + "-01-01", (currentYear + 1) + "-01-01"};
        }

        // "2026.4" 或 "2026.04" → 年月范围（支持单/双位月份）
        if (filter.matches("\\d{4}\\.\\d{1,2}")) {
            try {
                // 标准化：补零到 yyyy.MM 格式
                String[] parts = filter.split("\\.");
                String normalized = parts[0] + "." + String.format("%02d", Integer.parseInt(parts[1]));
                YearMonth ym = YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy.MM"));
                return new String[]{ym.atDay(1).toString(), ym.plusMonths(1).atDay(1).toString()};
            } catch (Exception e) {
                log.debug("日期解析失败: {}", filter, e);
                return new String[]{null, null};
            }
        }

        // "更早" → 今年之前
        if ("更早".equals(filter)) {
            return new String[]{null, currentYear + "-01-01"};
        }

        return new String[]{null, null};
    }
}
