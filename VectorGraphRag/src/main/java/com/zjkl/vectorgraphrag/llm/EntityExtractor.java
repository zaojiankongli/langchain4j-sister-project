package com.zjkl.vectorgraphrag.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zjkl.vectorgraphrag.graph.GraphBuilder.normalizePhrase;

/**
 * Extracts named entities from text for query processing (NER).
 * Used during query time to identify entities in the user's question.
 */
@Slf4j
public class EntityExtractor {

    private static final String SYSTEM_PROMPT =
            "你是一个高效的命名实体识别系统。";

    private static final String ONE_SHOT_INPUT =
            "请提取以下问题中所有对解答问题至关重要的命名实体。\n" +
            "以 JSON 格式输出命名实体列表。只输出 JSON，不要包含 markdown 代码块或其他内容。\n\n" +
            "问题：哪个杂志创刊更早，Arthur's Magazine 还是 First for Women？";

    private static final String ONE_SHOT_OUTPUT =
            "{\"named_entities\": [\"First for Women\", \"Arthur's Magazine\"]}";

    private static final String TEMPLATE = "\n问题：{}\n";

    private final OpenAiClient openAiClient;
    private final Gson gson;

    public EntityExtractor(VectorGraphRagSettings settings, OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
        this.gson = new Gson();
    }

    public List<String> extract(String question) {
        if (question == null || question.trim().isEmpty()) return List.of();

        try {
            List<Map<String, String>> examples = List.of(
                    Map.of("user", ONE_SHOT_INPUT, "assistant", ONE_SHOT_OUTPUT)
            );
            String response = openAiClient.chat(SYSTEM_PROMPT, examples,
                    TEMPLATE.replace("{}", question));

            return parseResponse(response);
        } catch (Exception e) {
            log.debug("Entity extraction failed, returning empty: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseResponse(String response) {
        try {
            String cleaned = OpenAiClient.cleanMarkdownCodeBlock(response);
            JsonObject json = gson.fromJson(cleaned, JsonObject.class);
            JsonArray entities = json.getAsJsonArray("named_entities");
            if (entities == null) {
                entities = json.getAsJsonArray("entities");
            }
            if (entities == null) return List.of();

            List<String> results = new ArrayList<>();
            for (int i = 0; i < entities.size(); i++) {
                String entity = entities.get(i).getAsString();
                if (entity != null && !entity.trim().isEmpty()) {
                    results.add(normalizePhrase(entity));
                }
            }
            return results;
        } catch (JsonSyntaxException e) {
            log.debug("Failed to parse NER response: {}", e.getMessage());
            return List.of();
        }
    }
}
