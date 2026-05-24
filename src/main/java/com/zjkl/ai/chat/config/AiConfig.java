package com.zjkl.ai.chat.config;

import com.zjkl.ai.summary.service.SummaryService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * AI 服务配置类
 */
@Configuration
public class AiConfig {

    // ========== SummaryService (AiServices 代理) ==========
    @Bean("summaryService")
    public SummaryService summaryService(QwenChatModel qwenChatModel) {
        return AiServices.builder(SummaryService.class)
                .chatModel(qwenChatModel)
                .build();
    }
}
