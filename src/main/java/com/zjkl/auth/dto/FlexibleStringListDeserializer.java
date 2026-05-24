package com.zjkl.auth.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字符串列表反序列化器
 */
public class FlexibleStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
            // 数组格式：["游戏", "编程"]
            List<String> result = new ArrayList<>();
            while (p.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                String value = p.getValueAsString();
                if (value != null && !value.isBlank()) {
                    // 如果数组元素本身包含逗号，也做拆分
                    for (String part : value.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            result.add(trimmed);
                        }
                    }
                }
            }
            return result;
        } else if (p.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
            // 字符串格式："游戏" 或 "游戏,编程"
            String value = p.getValueAsString();
            if (value == null || value.isBlank()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
