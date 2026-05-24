package com.zjkl.peek.config;

import com.zjkl.peek.agent.PeekContentAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${langchain4j.community.dashscope.chat-model.model-name:qwen3.5-flash}")
    private String modelName;

    private final QwenChatModel qwenChatModel;

    @Bean
    public PeekContentAgent peekContentAgent() {
        log.info("创建 PeekContentAgent，使用模型：{}（无工具模式）", modelName);
        return AgenticServices
                .agentBuilder(PeekContentAgent.class)
                .chatModel(qwenChatModel)
                .build();
    }
}
