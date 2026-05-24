package com.zjkl.emotion.config;

import com.zjkl.emotion.assistant.EmotionReasonAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmotionReasonAgentConfig {

    @Bean
    public EmotionReasonAgent emotionReasonAgent(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(EmotionReasonAgent.class)
                .chatModel(qwenChatModel)
                .build();
    }
}
