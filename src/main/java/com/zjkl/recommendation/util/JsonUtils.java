package com.zjkl.recommendation.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtils {

    private static final Gson GSON = new Gson();

    public static String stripMarkdownJson(String text) {
        if (text == null) return "[]";
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public static JsonArray parseJsonArray(String json) {
        try {
            JsonElement el = JsonParser.parseString(stripMarkdownJson(json));
            if (el.isJsonArray()) {
                return el.getAsJsonArray();
            }
        } catch (Exception e) {
            // fall through
        }
        return new JsonArray();
    }

    public static JsonObject parseJsonObject(String json) {
        try {
            JsonElement el = JsonParser.parseString(stripMarkdownJson(json));
            if (el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Exception e) {
            // fall through
        }
        return new JsonObject();
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    private JsonUtils() {}
}
