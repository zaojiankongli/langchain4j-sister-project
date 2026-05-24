package com.zjkl.ai.image.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.user.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.of;

/**
 * 通义万相图片生成服务
 * 使用 wan2.6-image 模型进行风格迁移
 * 采用异步提交 + 轮询查询结果模式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WanxImageService {

    private final HttpClientUtil httpClientUtil;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.community.dashscope.chat-model.api-key}")
    private String dashscopeApiKey;

    @Value("${wanx.reference-image-url}")
    private String referenceImageUrl;

    /** 异步提交任务端点 */
    private static final String ASYNC_API_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation";

    /** 任务查询端点 */
    private static final String TASK_QUERY_URL =
        "https://dashscope.aliyuncs.com/api/v1/tasks/";

    private static final String MODEL_NAME = "wan2.6-image";

    private static final String TEMPLATE_KEY = "image-generation";

    /** 首次轮询延迟（毫秒），任务刚提交不必等太久 */
    private static final long FIRST_POLL_DELAY_MS = 2000;

    /** 后续轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 5000;

    /** 最大轮询次数（首次 2s + 后续 5s * 59 ≈ 5 分钟超时） */
    private static final int MAX_POLL_COUNT = 60;

    /**
     * 生成图片（异步提交 + 轮询结果）
     *
     * @param elements 图片元素
     * @return 生成的图片 URL
     */
    public String generate(ImageElements elements) {
        try {
            // Step 1: 构建 prompt
            String prompt = buildPrompt(elements);
            log.info("开始调用通义万相（异步模式），prompt: {}", prompt);

            // Step 2: 提交异步任务
            String taskId = submitTask(prompt);
            log.info("通义万相异步任务已提交，taskId: {}", taskId);

            // Step 3: 轮询查询结果
            String imageUrl = pollTaskResult(taskId);
            log.info("通义万相生成图片成功：{}", imageUrl);
            return imageUrl;

        } catch (Exception e) {
            throw new RuntimeException("图片生成失败：" + e.getMessage(), e);
        }
    }

    /**
     * 提交异步图片生成任务
     *
     * @param prompt 图片生成提示词
     * @return 阿里云任务 ID
     */
    private String submitTask(String prompt) {
        try {
            Map<String, Object> requestBody = buildBody(prompt);

            Map<String, String> headers = of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + dashscopeApiKey,
                "X-DashScope-Async", "enable"
            );

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String responseBody = httpClientUtil.post(ASYNC_API_URL, headers, requestBodyJson);

            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            JsonNode output = jsonResponse.get("output");

            if (output != null && output.has("task_id")) {
                String taskId = output.get("task_id").asText();
                String taskStatus = output.has("task_status") ? output.get("task_status").asText() : "UNKNOWN";
                log.info("异步任务提交成功，taskId={}, status={}", taskId, taskStatus);
                return taskId;
            }

            throw new RuntimeException("异步任务提交返回格式异常，taskId 缺失");
 
         } catch (Exception e) {
             throw new RuntimeException("异步任务提交失败：" + e.getMessage(), e);
         }
    }

    /**
     * 轮询查询任务结果
     *
     * @param taskId 任务 ID
     * @return 生成的图片 URL
     */
    private String pollTaskResult(String taskId) {
        Map<String, String> headers = of(
            "Authorization", "Bearer " + dashscopeApiKey
        );

        String queryUrl = TASK_QUERY_URL + taskId;

        for (int i = 0; i < MAX_POLL_COUNT; i++) {
            try {
                // 首次短等 2s，后续 5s
                long delay = (i == 0) ? FIRST_POLL_DELAY_MS : POLL_INTERVAL_MS;
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("轮询被中断", e);
            }

            try {
                String responseBody = httpClientUtil.get(queryUrl, headers);
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                JsonNode output = jsonResponse.get("output");

                if (output == null) {
                    log.warn("任务查询返回无 output，taskId={}，response={}", taskId, responseBody);
                    continue;
                }

                String status = output.has("task_status") ? output.get("task_status").asText() : "UNKNOWN";

                switch (status) {
                    case "SUCCEEDED" -> {
                        return extractImageUrl(output);
                    }
                    case "FAILED", "CANCELED" -> {
                        String message = output.has("message") ? output.get("message").asText() : "未知原因";
                        String code = output.has("code") ? output.get("code").asText() : "UNKNOWN";
                        throw new RuntimeException("任务" + status + "，code=" + code + "，message=" + message);
                    }
                    case "PENDING", "RUNNING" -> {
                        if (i % 6 == 0) {
                            long elapsedMs = (i == 0 ? FIRST_POLL_DELAY_MS : i * POLL_INTERVAL_MS);
                            log.info("任务进行中，taskId={}，status={}，已等待 {}s", taskId, status, elapsedMs / 1000);
                        }
                    }
                    default -> log.warn("未知任务状态，taskId={}，status={}", taskId, status);
                }

            } catch (Exception e) {
                log.warn("查询任务结果异常，taskId={}，第{}次重试：{}", taskId, i + 1, e.getMessage());
            }
        }

        throw new RuntimeException("任务超时，taskId=" + taskId + "，超过 " + (MAX_POLL_COUNT * POLL_INTERVAL_MS / 1000) + "s 未完成");
    }

    /**
     * 从任务结果中提取图片 URL
     */
    private String extractImageUrl(JsonNode output) {
        // 异步返回格式：output.choices[0].message.content[0].image
        if (output.has("choices")) {
            JsonNode choices = output.get("choices");
            if (choices.size() > 0
                    && choices.get(0).has("message")
                    && choices.get(0).get("message").has("content")) {
                JsonNode contentArray = choices.get(0).get("message").get("content");
                for (var item : contentArray) {
                    if (item.has("image")) {
                        return item.get("image").asText();
                    }
                }
            }
        }

        log.error("无法从任务结果中提取图片 URL，output={}", output);
        throw new RuntimeException("无法从任务结果中提取图片 URL");
    }

    private Map<String, Object> buildBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL_NAME);

        // input
        Map<String, Object> input = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, String>> content = new ArrayList<>();
        Map<String, String> textItem = new HashMap<>();
        textItem.put("text", prompt);
        content.add(textItem);

        Map<String, String> imageItem = new HashMap<>();
        imageItem.put("image", referenceImageUrl);
        content.add(imageItem);

        message.put("content", content);
        messages.add(message);
        input.put("messages", messages);
        requestBody.put("input", input);

        // parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("negative_prompt", "低分辨率，低画质，AI 感，过度光滑，变形，畸形");
        parameters.put("size", "1K");
        parameters.put("n", 1);
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("enable_interleave", false);

        requestBody.put("parameters", parameters);
        return requestBody;
    }

    /**
     * 构建图片生成 prompt
     */
    private String buildPrompt(ImageElements elements) {
        String clothingDetail = elements.getClothingDetail() != null
            ? "（" + elements.getClothingDetail() + "）"
            : "";

        String sceneDetail = elements.getSceneDetail() != null
            ? "（" + elements.getSceneDetail() + "）"
            : "";

        String props = elements.getKeyProps() != null && !elements.getKeyProps().isEmpty()
            ? String.join("、", elements.getKeyProps())
            : "无特定道具";

        Map<String, Object> variables = Map.of(
            "clothing", elements.getClothing(),
            "clothingDetail", clothingDetail,
            "atmosphere", elements.getAtmosphere(),
            "scene", elements.getScene(),
            "sceneDetail", sceneDetail,
            "timeOfDay", elements.getTimeOfDay(),
            "props", props,
            "emotion", elements.getEmotion()
        );

        return promptTemplateService.render(TEMPLATE_KEY, variables);
    }
}
