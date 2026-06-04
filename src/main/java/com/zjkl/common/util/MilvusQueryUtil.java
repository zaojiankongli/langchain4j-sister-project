package com.zjkl.common.util;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 查询公共工具类
 * <p>
 * 抽取 GraphEntityService / GraphQueryService / GraphSnapshotService 中重复的查询逻辑，
 * 包括 filter 构建、查询执行、JSON 提取和字符串转义。
 */
public final class MilvusQueryUtil {

    /** 默认查询结果上限，防止无分页查询导致 OOM */
    private static final int DEFAULT_QUERY_LIMIT = 500;

    private MilvusQueryUtil() {}

    /**
     * 按 filter 条件查询 Milvus 集合（使用默认上限 500 条）
     */
    public static List<Map<String, Object>> queryByFilter(
            MilvusClientV2 client, String collectionName, String filter, List<String> outputFields) {
        return queryByFilter(client, collectionName, filter, outputFields, DEFAULT_QUERY_LIMIT);
    }

    /**
     * 按 filter 条件查询 Milvus 集合（指定上限）
     */
    public static List<Map<String, Object>> queryByFilter(
            MilvusClientV2 client, String collectionName, String filter,
            List<String> outputFields, int limit) {
        QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(outputFields)
                .limit(limit)
                .build());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (resp.getQueryResults() == null) {
            return rows;
        }
        for (QueryResp.QueryResult result : resp.getQueryResults()) {
            rows.add(new HashMap<>(result.getEntity()));
        }
        return rows;
    }

    /**
     * 构建 user_id 过滤条件
     */
    public static String userFilter(String userId) {
        return "user_id == \"" + escape(userId) + "\"";
    }

    /**
     * 从 LLM 返回的文本中提取第一个完整的 JSON 对象（花括号匹配）
     */
    public static String extractJson(String text) {
        int start = text.indexOf('{');
        if (start < 0) return text;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return text.substring(start, i + 1);
                }
            }
        }
        return text.substring(start);
    }

    /**
     * 标准化短语：去除特殊字符，转小写
     */
    public static String normalizePhrase(String phrase) {
        return phrase == null ? ""
                : phrase.replaceAll("[^\\p{L}0-9 ]", " ").replaceAll("\\s+", " ").toLowerCase().trim();
    }

    /**
     * 转义 Milvus 过滤表达式中的字符串值。
     * 处理: \, ", \n, \r, \0
     * 注意: 不转义 Milvus 表达式关键字 (and, or, not, in, like 等)，
     * 因为此方法仅用于已加引号的字符串值内部，关键字不会被解析。
     */
    public static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\0", "");
    }
}
