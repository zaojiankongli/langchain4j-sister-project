package com.zjkl.ai.summary.config;

import com.zjkl.ai.summary.agent.*;
import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 摘要 Agent 工作流配置
 */
@Configuration
public class AgentConfig {
    
    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);


    @Bean
    public SummaryAgent1 summaryAgent1(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent1.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v1")
                .async(true)
                .build();
    }

    @Bean
    public SummaryAgent2 summaryAgent2(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent2.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v2")
                .async(true)
                .build();
    }

    @Bean
    public SummaryAgent3 summaryAgent3(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent3.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v3")
                .async(true)
                .build();
    }



    @Bean
    public ScorerAgent scorerAgent(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(ScorerAgent.class)
                .chatModel(qwenChatModel)
                .outputKey("score")
                .async(true)
                .build();
    }


    @Bean
    public DailySummaryWorkflow dailySummaryWorkflow(
            SummaryAgent1 agent1,
            SummaryAgent2 agent2,
            SummaryAgent3 agent3,
            ScorerAgent scorerAgent) {
        
        return AgenticServices
            .parallelBuilder(DailySummaryWorkflow.class)
            .subAgents(agent1, agent2, agent3)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .outputKey("summaries")
            .output(agenticScope -> {
                List<DailySummaryResult> summaries = agenticScope.readState("summaries", List.of());
                String conversation = agenticScope.readState("conversation", "");
                
                double maxScore = -1;
                DailySummaryResult bestResult = null;
                
                for (DailySummaryResult result : summaries) {
                    if (result == null || result.summary() == null || result.summary().isEmpty()) {
                        log.warn("摘要生成失败，跳过");
                        continue;
                    }
                    
                    agenticScope.writeState("summary", result.summary());
                    agenticScope.writeState("conversation", conversation);
                    Integer score = scorerAgent.score(result.summary(), conversation);
                    
                    log.debug("摘要评分 - 标题：{}, 评分：{}", result.title(), score);
                    
                    if (score > maxScore) {
                        maxScore = score;
                        bestResult = result;
                    }
                }
                
                if (bestResult == null) {
                    log.error("所有摘要生成失败，返回空结果");
                    return new DailySummaryResult("", "");
                }
                
                log.info("选择最佳摘要 - 标题：{}, 评分：{}, 摘要长度：{}", 
                    bestResult.title(), maxScore, bestResult.summary().length());
                
                return bestResult;
            })
            .build();
    }
}
