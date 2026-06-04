package com.zjkl.vectorgraphrag.llm;

import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Generates answers using retrieved passage context via LLM.
 */
@Slf4j
public class AnswerGenerator {

    private static final String ANSWER_PROMPT =
            "请根据以下检索到的上下文内容回答用户的问题。\n" +
            "如果检索到的上下文不足以回答问题，请直接说不知道，不要编造信息。\n\n" +
            "问题：{question}\n\n" +
            "上下文：\n{context}\n\n" +
            "答案：";

    private final OpenAiClient openAiClient;
    private final VectorGraphRagSettings settings;

    public AnswerGenerator(VectorGraphRagSettings settings, OpenAiClient openAiClient) {
        this.settings = settings;
        this.openAiClient = openAiClient;
    }

    public String generate(String question, List<String> passages) {
        String context = String.join("\n\n", passages);
        String prompt = ANSWER_PROMPT
                .replace("{question}", question)
                .replace("{context}", context);

        try {
            return openAiClient.chatWithMessages(List.of(
                    java.util.Map.of("role", "user", "content", prompt)
            ));
        } catch (Exception e) {
            log.warn("Answer generation failed: {}", e.getMessage());
            return "I don't know.";
        }
    }
}
