package com.zjkl.wakeup.config;

import com.zjkl.common.config.properties.AiProperties;
import com.zjkl.wakeup.agent.*;
import com.zjkl.wakeup.tools.WakeUpTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 主动唤醒 Agent 配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WakeUpAgentConfig {

    private final QwenChatModel qwenChatModel;
    private final WakeUpTools wakeUpTools;
    private final AiProperties aiProperties;

    @Bean
    public WakeUpGenerator1Agent wakeUpGenerator1Agent() {
        log.info("创建 WakeUpGenerator1Agent（侧重记忆），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpGenerator1Agent.class)
                .chatModel(qwenChatModel)
                .tools(wakeUpTools)
                .maxSequentialToolsInvocations(5)
                .build();
    }

    @Bean
    public WakeUpGenerator2Agent wakeUpGenerator2Agent() {
        log.info("创建 WakeUpGenerator2Agent（侧重聊天），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpGenerator2Agent.class)
                .chatModel(qwenChatModel)
                .tools(wakeUpTools)
                .maxSequentialToolsInvocations(5)
                .build();
    }

    @Bean
    public WakeUpGenerator3Agent wakeUpGenerator3Agent() {
        log.info("创建 WakeUpGenerator3Agent（侧重锚点），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpGenerator3Agent.class)
                .chatModel(qwenChatModel)
                .tools(wakeUpTools)
                .maxSequentialToolsInvocations(5)
                .build();
    }

    @Bean
    public WakeUpScorer1Agent wakeUpScorer1Agent() {
        log.info("创建 WakeUpScorer1Agent（评分-记忆），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpScorer1Agent.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public WakeUpScorer2Agent wakeUpScorer2Agent() {
        log.info("创建 WakeUpScorer2Agent（评分-聊天），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpScorer2Agent.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public WakeUpScorer3Agent wakeUpScorer3Agent() {
        log.info("创建 WakeUpScorer3Agent（评分-锚点），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpScorer3Agent.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public WakeUpArbiterAgent wakeUpArbiterAgent() {
        log.info("创建 WakeUpArbiterAgent（仲裁），使用模型：{}", aiProperties.getChatModelName());
        return AgenticServices.agentBuilder(WakeUpArbiterAgent.class)
                .chatModel(qwenChatModel)
                .build();
    }
}
