package com.zjkl.ai.summary.config;

import com.zjkl.ai.summary.agent.DailySummaryWorkflow;
import com.zjkl.ai.summary.agent.ScorerAgent;
import com.zjkl.ai.summary.agent.SummaryAgent;
import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 摘要 Agent 工作流配置
 * 创建 3 个相同接口的 agent 实例（相同 prompt），通过 scorer 选择最佳结果
 */
@Configuration
public class AgentConfig {
    
    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    public SummaryAgent summaryAgent1(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v1")
                .async(true)
                .build();
    }

    @Bean
    public SummaryAgent summaryAgent2(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v2")
                .async(true)
                .build();
    }

    @Bean
    public SummaryAgent summaryAgent3(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(SummaryAgent.class)
                .chatModel(qwenChatModel)
                .outputKey("summary_v3")
                .async(true)
                .build();
    }

    @Bean
    public ScorerAgent scorerAgent(QwenChatModel qwenChatModel) {
        return AgenticServices.agentBuilder(ScorerAgent.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public DailySummaryWorkflow dailySummaryWorkflow(
            @Qualifier("summaryAgent1") SummaryAgent agent1,
            @Qualifier("summaryAgent2") SummaryAgent agent2,
            @Qualifier("summaryAgent3") SummaryAgent agent3,
            ScorerAgent scorerAgent) {
        
        return AgenticServices
            .parallelBuilder(DailySummaryWorkflow.class)
            .subAgents(agent1, agent2, agent3)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .outputKey("summaries")
            .output(agenticScope -> {
                List<DailySummaryResult> summaries = agenticScope.readState("summaries", List.of());
                String conversation = agenticScope.readState("conversation", "");

                // 过滤空结果
                List<DailySummaryResult> validSummaries = new ArrayList<>();
                for (DailySummaryResult r : summaries) {
                    if (r != null && r.summary() != null && !r.summary().isBlank()) {
                        validSummaries.add(r);
                    }
                }
                if (validSummaries.isEmpty()) {
                    log.error("所有摘要生成失败，返回空结果");
                    return new DailySummaryResult("", "", 0.0, "平静");
                }
                if (validSummaries.size() == 1) {
                    return validSummaries.get(0);
                }

                // 批量打分（1 次 LLM 调用取代 3 次串行）
                List<Integer> scores;
                try {
                    scores = scorerAgent.batchScore(
                            conversation,
                            validSummaries.get(0).summary(),
                            validSummaries.size() > 1 ? validSummaries.get(1).summary() : "",
                            validSummaries.size() > 2 ? validSummaries.get(2).summary() : "");
                } catch (Exception e) {
                    log.warn("批量评分失败，返回第一份摘要: {}", e.getMessage());
                    return validSummaries.get(0);
                }

                // 选最高分
                double maxScore = -1;
                DailySummaryResult bestResult = validSummaries.get(0);
                for (int i = 0; i < Math.min(scores.size(), validSummaries.size()); i++) {
                    int score = scores.get(i) != null ? scores.get(i) : 0;
                    log.debug("摘要评分 - 标题：{}, 评分：{}", validSummaries.get(i).title(), score);
                    if (score > maxScore) {
                        maxScore = score;
                        bestResult = validSummaries.get(i);
                    }
                }

                log.info("选择最佳摘要 - 标题：{}, 评分：{}", bestResult.title(), (int) maxScore);
                return bestResult;
            })
            .build();
    }
}
