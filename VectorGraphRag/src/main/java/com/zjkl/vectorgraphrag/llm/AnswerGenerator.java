package com.zjkl.vectorgraphrag.llm;

import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Generates answers using retrieved passage context via LLM.
 */
@Slf4j
public class AnswerGenerator {

    private static final String ANSWER_PROMPT_TEMPLATE =
            "请根据以下检索到的上下文内容回答用户的问题。\n" +
            "如果检索到的上下文不足以回答问题，请直接说不知道，不要编造信息。\n\n" +
            "问题：%s\n\n" +
            "上下文：\n%s\n\n" +
            "答案：";

    private final OpenAiClient openAiClient;
    private final VectorGraphRagSettings settings;

    public AnswerGenerator(VectorGraphRagSettings settings, OpenAiClient openAiClient) {
        this.settings = settings;
        this.openAiClient = openAiClient;
    }

    public String generate(String question, List<String> passages) {
        String context = String.join("\n\n", passages);
        // Use String.format instead of .replace() to prevent template injection
        // (e.g., question containing "{context}" would corrupt the prompt)
        String prompt = String.format(ANSWER_PROMPT_TEMPLATE, question, context);

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
