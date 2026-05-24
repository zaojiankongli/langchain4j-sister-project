package com.zjkl.ai.image.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationMessage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalMessageItemImage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalMessageItemText;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * 图片描述服务
 * 使用阿里云 VL 模型提取图片描述
 */
@Slf4j
@Service
public class ImageDescriptionService {

    @Value("${langchain4j.community.dashscope.vision-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.community.dashscope.vision-model.model-name}")
    private String modelName;

    private MultiModalConversation conversation;

    @PostConstruct
    public void init() {
        this.conversation = new MultiModalConversation();
    }

    /**
     * 提取图片描述
     *
     * @param imageUrl OSS 图片 URL
     * @return 图片的文本描述
     */
    public String describe(String imageUrl) {
        String prompt = "从温柔乖巧的20岁妹妹的角度，描述图片内容";
        return callVisionModel(imageUrl, prompt);
    }

    /**
     * Peek 专用：描述屏幕截图内容
     * 聚焦用户行为和屏幕上的应用/网页，而非通用描述
     *
     * @param imageUrl OSS 截图 URL
     * @return 屏幕内容简述（30 字以内）
     */
    public String describeForPeek(String imageUrl) {
        String prompt = "这是一张电脑截图。请描述屏幕上显示的内容，包括：1) 用户在用什么应用或网页 2) 用户当前在做什么。30字以内，直接输出。";
        return callVisionModel(imageUrl, prompt);
    }

    private String callVisionModel(String imageUrl, String prompt) {
        try {
            log.debug("开始提取图片描述: {}", imageUrl);

            MultiModalConversationMessage userMessage = MultiModalConversationMessage.builder()
                    .role("user")
                    .content(List.of(
                            new MultiModalMessageItemImage(imageUrl),
                            new MultiModalMessageItemText(prompt)
                    ))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model(modelName)
                    .messages(List.of(userMessage))
                    .apiKey(apiKey)
                    .build();

            MultiModalConversationResult result = conversation.call(param);

            String description = extractText(result);
            log.info("图片描述提取成功: {} -> {}", imageUrl, description);
            return description;

        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            log.error("图片描述提取失败: {}", imageUrl, e);
            return "[图片描述提取失败]";
        }
    }

    private String extractText(MultiModalConversationResult result) {
        try {
            MultiModalConversationOutput output = result.getOutput();
            if (output == null) {
                return "无法解析描述";
            }
            List<MultiModalConversationOutput.Choice> choices = output.getChoices();
            if (choices == null || choices.isEmpty()) {
                return "无法解析描述";
            }
            MultiModalMessage message = choices.get(0).getMessage();
            if (message == null) {
                return "无法解析描述";
            }
            List<Map<String, Object>> contents = message.getContent();
            if (contents == null) {
                return "无法解析描述";
            }
            for (Map<String, Object> item : contents) {
                Object text = item.get("text");
                if (text != null && !text.toString().isEmpty()) {
                    return text.toString();
                }
            }
            return "无法解析描述";
        } catch (Exception e) {
            log.warn("解析描述失败", e);
            return "解析描述失败";
        }
    }
}
