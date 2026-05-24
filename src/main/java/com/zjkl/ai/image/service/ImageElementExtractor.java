package com.zjkl.ai.image.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从记忆提取可视化场景元素
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageElementExtractor {
    
    private final QwenChatModel qwenChatModel;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    
    private static final String TEMPLATE_KEY = "image-extraction";


    public ImageElements extract(String memoryContent) {
        try {
            // 使用模板服务渲染
            String prompt = promptTemplateService.render(TEMPLATE_KEY, Map.of(
                "memoryContent", memoryContent
            ));
            
            // 调用 LLM
            UserMessage message = UserMessage.from(prompt);
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(message)
                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormatType.JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("ImageElements")
                                    .rootElement(JsonObjectSchema.builder()
                                        .addProperty("clothing", JsonStringSchema.builder()
                                            .description("人物穿着类别：校服、休闲装、运动装、家居服、正装、其他")
                                            .build())
                                        .addProperty("clothingDetail", JsonStringSchema.builder()
                                            .description("具体穿着描述（可选），如：白色衬衫 + 蓝色百褶裙、灰色连帽卫衣")
                                            .build())
                                        .addProperty("scene", JsonStringSchema.builder()
                                            .description("场景环境类别：教室、卧室、客厅、公园、街道、咖啡厅、图书馆、海边、山林、其他")
                                            .build())
                                        .addProperty("sceneDetail", JsonStringSchema.builder()
                                            .description("场景详细描述（可选），如：洒满阳光的窗边书桌、开满樱花的公园长椅")
                                            .build())
                                        .addProperty("timeOfDay", JsonStringSchema.builder()
                                            .description("时间：清晨、上午、中午、下午、黄昏、夜晚、深夜")
                                            .build())
                                        .addProperty("atmosphere", JsonStringSchema.builder()
                                            .description("整体氛围：温馨、欢快、安静、浪漫、兴奋、沉思、忧郁、其他")
                                            .build())
                                        .addProperty("keyProps", JsonArraySchema.builder()
                                            .description("关键道具列表（最多 3 个），如：[书本，咖啡，相机]")
                                            .items(new JsonStringSchema())
                                            .build())
                                        .addProperty("emotion", JsonStringSchema.builder()
                                            .description("人物情绪：开心、平静、期待、思念、兴奋、疲惫、其他")
                                            .build())
                                        .addProperty("reason", JsonStringSchema.builder()
                                            .description("选择这个场景的理由（50 字内）")
                                            .build())
                                        .required(List.of("clothing", "scene", "timeOfDay", "atmosphere", "emotion", "reason"))
                                        .build())
                                    .build())
                            .build())
                    .build());
            
            String json = response.aiMessage().text();
            
            // 解析 JSON
            ImageElements elements = objectMapper.readValue(json, ImageElements.class);
            
            // 验证必填字段
            validateAndFillDefaults(elements);
            
            log.info("图片元素提取成功：clothing={}, scene={}, atmosphere={}", 
                elements.getClothing(), elements.getScene(), elements.getAtmosphere());
            
            return elements;
            
        } catch (JsonProcessingException e) {
            log.error("图片元素提取失败：JSON 解析错误", e);
            return createDefaultElements();
        } catch (Exception e) {
            log.error("图片元素提取失败", e);
            return createDefaultElements();
        }
    }
    
    private void validateAndFillDefaults(ImageElements elements) {
        if (elements == null) {
            throw new IllegalArgumentException("提取结果为 null");
        }
        
        // 必填字段默认值
        if (elements.getClothing() == null) {
            elements.setClothing("休闲装");
        }
        if (elements.getScene() == null) {
            elements.setScene("温馨室内");
        }
        if (elements.getTimeOfDay() == null) {
            elements.setTimeOfDay("白天");
        }
        if (elements.getAtmosphere() == null) {
            elements.setAtmosphere("安静");
        }
        if (elements.getEmotion() == null) {
            elements.setEmotion("平静");
        }
        if (elements.getReason() == null) {
            elements.setReason("记忆内容无明显场景线索，使用默认值");
        }
        
        // 可选字段处理
        if (elements.getKeyProps() == null) {
            elements.setKeyProps(Collections.emptyList());
        }
    }
    
    private ImageElements createDefaultElements() {
        return ImageElements.builder()
            .clothing("休闲装")
            .scene("温馨室内")
            .timeOfDay("白天")
            .atmosphere("安静")
            .emotion("平静")
            .reason("记忆内容无明显场景线索，使用默认值")
            .keyProps(Collections.emptyList())
            .build();
    }
}
