package com.zjkl.user.config;

import com.zjkl.user.assistant.TagGenerator;
import com.zjkl.user.assistant.TagScorer;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 兴趣标签生成 Agentic 工作流配置
 */
@Slf4j
@Configuration
public class InterestTagAiConfig {

    @Bean("tagWorkflow")
    public UntypedAgent tagWorkflow(QwenChatModel qwenChatModel) {

        // 1. TagGenerator (AI Agent)
        TagGenerator tagGenerator = AgenticServices
                .agentBuilder(TagGenerator.class)
                .chatModel(qwenChatModel)
                .build();

        // 2. TagScorer (AI Agent)
        TagScorer tagScorer = AgenticServices
                .agentBuilder(TagScorer.class)
                .chatModel(qwenChatModel)
                .build();

        // 3. Sequence: TagGenerator → TagScorer
        UntypedAgent workflow = AgenticServices
                .sequenceBuilder()
                .subAgents(tagGenerator, tagScorer)
                .outputKey("scoredTags")
                .errorHandler(this::handleWorkflowError)
                .build();

        log.info("兴趣标签生成工作流构建完成: Sequence(TagGenerator → TagScorer)");
        return workflow;
    }

    /**
     * 工作流错误处理
     */
    private ErrorRecoveryResult handleWorkflowError(ErrorContext errorContext) {
        log.error("兴趣标签生成工作流 Agent [{}] 执行失败: {}",
                errorContext.agentName(), errorContext.exception().getMessage());

        String agentName = errorContext.agentName();
        if ("tagGenerator".equals(agentName) || "tagScorer".equals(agentName)) {
            return ErrorRecoveryResult.retry();
        }

        return ErrorRecoveryResult.throwException();
    }
}