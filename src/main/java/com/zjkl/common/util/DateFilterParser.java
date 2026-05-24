package com.zjkl.common.util;

public class DateFilterParser {

    private DateFilterParser() {}

    public static String[] parse(String filter) {
        if (filter == null || filter.isBlank() || "最近".equals(filter)) {
            return new String[]{null, null};
        }
        return switch (filter) {
            case "2026年" -> new String[]{"2026-01-01", "2027-01-01"};
            case "2026.04" -> new String[]{"2026-04-01", "2026-05-01"};
            case "2026.03" -> new String[]{"2026-03-01", "2026-04-01"};
            case "更早" -> new String[]{null, "2026-01-01"};
            default -> new String[]{null, null};
        };
    }
}
