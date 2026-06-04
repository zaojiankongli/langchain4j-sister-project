package com.zjkl.vectorgraphrag.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import com.zjkl.vectorgraphrag.model.Document;
import com.zjkl.vectorgraphrag.model.Triplet;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts knowledge triplets (subject-predicate-object) from text using LLM.
 */
@Slf4j
public class TripletExtractor {

    private static final String SYSTEM_PROMPT =
            "你是一个专业的知识图谱构建专家。你的任务是从给定的文本中提取知识三元组。\n" +
            "\n" +
            "一个三元组由以下部分组成：\n" +
            "- 主体(Subject)：一个实体（人、地点、事物、概念等）\n" +
            "- 谓词(Predicate)：主体和客体之间的关系\n" +
            "- 客体(Object)：另一个实体\n" +
            "\n" +
            "准则：\n" +
            "1. 提取文本中所有有意义的语义关系\n" +
            "2. 保持实体名称简洁但完整\n" +
            "3. 使用清晰、具体的谓词\n" +
            "4. 同时提取显式和隐式的关系\n" +
            "5. 确保三元组在文本中有事实依据\n" +
            "\n" +
            "请以 JSON 对象形式返回结果，其中包含一个 \"triplets\" 数组，每个三元组是一个 [subject, predicate, object] 数组。只输出 JSON，不要包含 markdown 代码块或其他内容。";

    private static final String EXAMPLE_INPUT =
            "文本：爱因斯坦于1879年出生在德国的乌尔姆。他发展了相对论，彻底改变了物理学。爱因斯坦曾在普林斯顿高等研究院工作。";

    private static final String EXAMPLE_OUTPUT =
            "{\"triplets\": [[\"爱因斯坦\", \"出生于\", \"乌尔姆\"], [\"爱因斯坦\", \"出生于\", \"1879年\"], [\"爱因斯坦\", \"发展了\", \"相对论\"], [\"相对论\", \"改变了\", \"物理学\"], [\"爱因斯坦\", \"工作于\", \"普林斯顿高等研究院\"]]}";

    private final OpenAiClient openAiClient;
    private final Gson gson;

    public TripletExtractor(VectorGraphRagSettings settings, OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
        this.gson = new Gson();
    }

    public List<Triplet> extract(String text) {
        if (text == null || text.trim().isEmpty()) return List.of();

        try {
            List<Map<String, String>> examples = List.of(
                    Map.of("user", EXAMPLE_INPUT, "assistant", EXAMPLE_OUTPUT)
            );
            String response = openAiClient.chat(SYSTEM_PROMPT, examples, "Text: " + text);
            return parseResponse(response);
        } catch (Exception e) {
            log.debug("Triplet extraction failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Document> extractFromDocuments(List<Document> documents, boolean showProgress) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            List<Triplet> triplets = extract(doc.getText());
            doc.setTriplets(triplets);
            // Store as raw arrays in metadata for backward compat
            List<List<String>> rawTriplets = triplets.stream()
                    .map(t -> List.of(t.getSubject(), t.getPredicate(), t.getObject()))
                    .collect(Collectors.toList());
            doc.getMetadata().put("triplets", rawTriplets);
            if (showProgress) {
                log.info("Extracted triplets for doc {}/{}: {} triplets", i + 1, documents.size(), triplets.size());
            }
        }
        return documents;
    }

    private List<Triplet> parseResponse(String response) {
        try {
            String cleaned = OpenAiClient.cleanMarkdownCodeBlock(response);
            JsonObject json = gson.fromJson(cleaned, JsonObject.class);
            JsonArray tripletsArray = json.getAsJsonArray("triplets");
            if (tripletsArray == null) return List.of();

            List<Triplet> results = new ArrayList<>();
            for (int i = 0; i < tripletsArray.size(); i++) {
                JsonArray arr = tripletsArray.get(i).getAsJsonArray();
                if (arr.size() >= 3) {
                    String subject = arr.get(0).getAsString().trim();
                    String predicate = arr.get(1).getAsString().trim();
                    String object = arr.get(2).getAsString().trim();
                    if (!subject.isEmpty() && !predicate.isEmpty() && !object.isEmpty()) {
                        results.add(new Triplet(subject, predicate, object));
                    }
                }
            }
            return results;
        } catch (JsonSyntaxException e) {
            log.debug("Failed to parse triplet response: {}", e.getMessage());
            return List.of();
        }
    }
}
