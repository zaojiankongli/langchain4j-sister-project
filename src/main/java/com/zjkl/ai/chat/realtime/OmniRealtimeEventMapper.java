package com.zjkl.ai.chat.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Local protocol adapter for Qwen-Omni-Realtime server events.
 * Keeps JSON shape knowledge out of session lifecycle code.
 */
final class OmniRealtimeEventMapper {

    private OmniRealtimeEventMapper() {
    }

    static String eventType(JsonNode event) {
        return event.path("type").asText("");
    }

    static String errorMessage(JsonNode event, String fallback) {
        return event.path("error").path("message").asText(fallback);
    }

    static String assistantText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.hasNonNull("text")) {
            return node.path("text").asText("");
        }
        if (node.hasNonNull("transcript")) {
            return node.path("transcript").asText("");
        }
        JsonNode content = node.path("content");
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                String partText = part.path("text").asText("");
                if (isBlank(partText)) {
                    partText = part.path("transcript").asText("");
                }
                if (!isBlank(partText)) {
                    text.append(partText);
                }
            }
            return text.toString();
        }
        JsonNode output = node.path("output");
        if (output.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                text.append(assistantText(item));
            }
            return text.toString();
        }
        JsonNode item = node.path("item");
        if (!item.isMissingNode()) {
            return assistantText(item);
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
