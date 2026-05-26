package com.zjkl.ai.peek.config;

import com.zjkl.ai.peek.agent.PeekContentAgent;
import com.zjkl.common.config.properties.AiProperties;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Peek Agent 配置
 *
 * 与 WakeUpAgentConfig 的区别：不注入 tools
 * PeekContentAgent 的所有输入已由 VLM 预处理，不需要 ReAct 工具循环
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PeekAgentConfig {

    private final QwenChatModel qwenChatModel;
    private final AiProperties aiProperties;

    @Bean
    public PeekContentAgent peekContentAgent() {
        log.info("创建 PeekContentAgent，使用模型：{}（无工具模式）", aiProperties.getChatModelName());
        return AgenticServices
                .agentBuilder(PeekContentAgent.class)
                .chatModel(qwenChatModel)
                .build();
    }
}
