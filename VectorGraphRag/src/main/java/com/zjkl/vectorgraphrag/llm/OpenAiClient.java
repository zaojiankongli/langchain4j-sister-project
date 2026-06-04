package com.zjkl.vectorgraphrag.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI API client for chat completions and embeddings.
 * Uses manual retry with exponential backoff (Spring @Retryable not used
 * because this class is not a Spring-managed bean).
 */
@Slf4j
public class OpenAiClient {

    private final VectorGraphRagSettings settings;
    private final HttpClient httpClient;
    private final Gson gson;
    private final LLMCache cache;

    private static final String DEFAULT_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_EMBED_URL = "https://api.openai.com/v1/embeddings";

    public OpenAiClient(VectorGraphRagSettings settings, LLMCache cache) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.gson = new Gson();
        this.cache = cache;
    }

    /**
     * Chat completion with structured output (response_format: json_object).
     * Used by extractors and rerankers that need parsed JSON responses.
     */
    public String chat(String systemPrompt, List<Map<String, String>> fewShotExamples, String userPrompt) {
        String fullPrompt = systemPrompt + "\n" + userPrompt;
        if (cache != null && settings.isUseLlmCache()) {
            String cached = cache.get(settings.getLlmModel(), fullPrompt, settings.getLlmTemperature());
            if (cached != null) return cached;
        }

        JsonArray messages = new JsonArray();
        messages.add(buildMessage("system", systemPrompt));
        for (Map<String, String> example : fewShotExamples) {
            messages.add(buildMessage("user", example.get("user")));
            messages.add(buildMessage("assistant", example.get("assistant")));
        }
        messages.add(buildMessage("user", userPrompt));

        String response = executeChat(messages);

        String content = extractContentFromResponse(response);

        if (cache != null && settings.isUseLlmCache()) {
            cache.set(settings.getLlmModel(), fullPrompt, content, settings.getLlmTemperature());
        }

        return content;
    }

    /**
     * Chat completion with free-form messages (no response_format restriction).
     * Used by AnswerGenerator and similar free-text generation.
     */
    public String chatWithMessages(List<Map<String, String>> messagesList) {
        // Build cache key from the conversation
        String fullPrompt = messagesList.stream()
                .map(m -> m.get("role") + ": " + m.get("content"))
                .collect(Collectors.joining("\n"));

        if (cache != null && settings.isUseLlmCache()) {
            String cached = cache.get(settings.getLlmModel(), fullPrompt, settings.getLlmTemperature());
            if (cached != null) return cached;
        }

        JsonArray messages = new JsonArray();
        for (Map<String, String> msg : messagesList) {
            messages.add(buildMessage(msg.get("role"), msg.get("content")));
        }

        String response = executeChat(messages);

        String content = extractContentFromResponse(response);

        if (cache != null && settings.isUseLlmCache()) {
            cache.set(settings.getLlmModel(), fullPrompt, content, settings.getLlmTemperature());
        }

        return content;
    }

    /**
     * Embed a single text string into a vector.
     */
    public List<Float> embed(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.getEmbeddingModel());
        body.addProperty("input", text);

        String response = executeHttp(buildEmbedUrl(), body.toString(), settings.getLlmMaxRetries());
        return parseEmbeddingResponse(response, 0);
    }

    /**
     * Embed multiple texts in a single API call (batch).
     * Returns embeddings in the same order as input texts.
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.getEmbeddingModel());
        JsonArray inputs = new JsonArray();
        for (String t : texts) inputs.add(t);
        body.add("input", inputs);

        String response = executeHttp(buildEmbedUrl(), body.toString(), settings.getLlmMaxRetries());

        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
        if (jsonResponse == null) throw new RuntimeException("Failed to parse batch embedding response as JSON");
        JsonArray data = jsonResponse.getAsJsonArray("data");
        if (data == null || data.isEmpty()) throw new RuntimeException("Batch embedding response has no data");

        // Index by the "index" field in the response to preserve order
        List<List<Float>> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) results.add(null);

        for (int i = 0; i < data.size(); i++) {
            JsonObject item = data.get(i).getAsJsonObject();
            int idx = item.get("index").getAsInt();
            JsonArray embedding = item.getAsJsonArray("embedding");
            List<Float> vec = new ArrayList<>();
            for (int j = 0; j < embedding.size(); j++) {
                vec.add(embedding.get(j).getAsFloat());
            }
            results.set(idx, vec);
        }
        return results;
    }

    /**
     * Build and execute a chat request.
     * Note: response_format is intentionally NOT set here for Qwen compatibility.
     * JSON output is guided via prompt instructions in each extractor/reranker.
     */
    private String executeChat(JsonArray messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.getLlmModel());
        body.addProperty("temperature", settings.getLlmTemperature());
        body.add("messages", messages);

        return executeHttp(buildChatUrl(), body.toString(), settings.getLlmMaxRetries());
    }

    /**
     * Build the chat completions URL, respecting custom baseUrl.
     */
    private String buildChatUrl() {
        String baseUrl = settings.getOpenaiBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "chat/completions";
        }
        return DEFAULT_CHAT_URL;
    }

    /**
     * Build the embeddings URL, respecting custom baseUrl.
     */
    private String buildEmbedUrl() {
        String baseUrl = settings.getOpenaiBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "embeddings";
        }
        return DEFAULT_EMBED_URL;
    }

    /**
     * Execute an HTTP POST with manual retry and exponential backoff.
     * 4xx client errors are NOT retried; 5xx server errors and network failures are.
     */
    private String executeHttp(String url, String bodyJson, int maxRetries) {
        int attempt = 0;
        while (true) {
            try {
                String apiKey = settings.getOpenaiApiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("OpenAI API key is required");
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                }

                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    // Client error — not retryable
                    throw new RuntimeException("OpenAI API client error: " + response.statusCode()
                            + " " + response.body());
                }

                // Server error — retry
                attempt++;
                if (attempt >= maxRetries) {
                    throw new RuntimeException("OpenAI API error after " + attempt + " retries: "
                            + response.statusCode() + " " + response.body());
                }

                long delay = Math.min(2000L * (long) Math.pow(1.5, attempt - 1), 10000);
                log.warn("OpenAI API retry {}/{} after {}ms: status={}", attempt, maxRetries, delay, response.statusCode());
                Thread.sleep(delay);

            } catch (RuntimeException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("OpenAI API call interrupted", e);
            } catch (IOException e) {
                // HTTP 网络层异常（IOException）→ 重试
                attempt++;
                if (attempt >= maxRetries) {
                    throw new RuntimeException("OpenAI API call failed after " + attempt + " retries", e);
                }
                long delay = Math.min(2000L * (long) Math.pow(1.5, attempt - 1), 10000);
                log.warn("OpenAI API retry {}/{} after {}ms: {}", attempt, maxRetries, delay, e.getMessage());
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
            }
        }
    }

    /**
     * Parse a single embedding from the API response by index.
     */
    private List<Float> parseEmbeddingResponse(String response, int index) {
        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
        if (jsonResponse == null) throw new RuntimeException("Failed to parse embedding response as JSON");
        JsonArray data = jsonResponse.getAsJsonArray("data");
        if (data == null || data.size() <= index) {
            throw new RuntimeException("Embedding response has no data at index " + index);
        }
        JsonObject item = data.get(index).getAsJsonObject();
        if (item == null) throw new RuntimeException("Embedding data item is null at index " + index);
        JsonArray embedding = item.getAsJsonArray("embedding");
        if (embedding == null) throw new RuntimeException("Embedding array is null at index " + index);
        List<Float> result = new ArrayList<>(embedding.size());
        for (int i = 0; i < embedding.size(); i++) {
            result.add(embedding.get(i).getAsFloat());
        }
        return result;
    }

    private JsonObject buildMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    /**
     * 从 LLM 响应中安全提取 content 字段，逐层 null 检查避免 NPE/ClassCastException
     */
    private String extractContentFromResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("Empty LLM response");
        }
        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
        if (jsonResponse == null) {
            throw new RuntimeException("Failed to parse LLM response as JSON");
        }
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM response has no choices");
        }
        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        if (firstChoice == null) {
            throw new RuntimeException("LLM response first choice is null");
        }
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null) {
            throw new RuntimeException("LLM response has no message");
        }
        var contentElem = message.get("content");
        if (contentElem == null || contentElem.isJsonNull()) {
            throw new RuntimeException("LLM response message has no content");
        }
        return contentElem.getAsString();
    }

    /**
     * 清洗 LLM 输出中的 markdown 代码块包裹（```json ... ```）
     * 在 EntityExtractor / TripletExtractor / LLMReranker 的 parseResponse 中调用
     */
    public static String cleanMarkdownCodeBlock(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        // 去除开头的 ```json 或 ```
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            // 去除结尾的 ```
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            return trimmed.trim();
        }
        return trimmed;
    }
}
