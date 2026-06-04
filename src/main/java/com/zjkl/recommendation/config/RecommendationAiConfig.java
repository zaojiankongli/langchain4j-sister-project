package com.zjkl.recommendation.config;

import com.zjkl.recommendation.assistant.FeedbackExtractor;
import com.zjkl.recommendation.assistant.ImageUrlFetcher;
import com.zjkl.recommendation.assistant.ProfileFetcher;
import com.zjkl.recommendation.assistant.RecommendAccumulator;
import com.zjkl.recommendation.assistant.ResourceRecommender;
import com.zjkl.recommendation.assistant.ResultScorer;
import com.zjkl.recommendation.util.JsonUtils;
import com.zjkl.recommendation.util.RecommendationConstants;
import com.zjkl.user.mapper.UserProfileMapper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.mcp.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 推荐工作流配置
 */
@Slf4j
@Configuration
public class RecommendationAiConfig {

    @Bean("recommendationWorkflow")
    public UntypedAgent recommendationWorkflow(
            QwenChatModel qwenChatModel,
            McpToolProvider mcpToolProvider,
            UserProfileMapper userProfileMapper) {

        ProfileFetcher profileFetcher = new ProfileFetcher(userProfileMapper);

        ResourceRecommender recommender = AgenticServices
                .agentBuilder(ResourceRecommender.class)
                .chatModel(qwenChatModel)
                .toolProviders(mcpToolProvider)
                .build();

        ResultScorer scorer = AgenticServices
                .agentBuilder(ResultScorer.class)
                .chatModel(qwenChatModel)
                .build();

        RecommendAccumulator accumulator = new RecommendAccumulator();

        FeedbackExtractor feedbackExtractor = new FeedbackExtractor();

        UntypedAgent searchScoreLoop = AgenticServices
                .loopBuilder()
                .subAgents(recommender, scorer, accumulator, feedbackExtractor)
                .maxIterations(RecommendationConstants.MAX_ITERATIONS)
                .testExitAtLoopEnd(true)
                .exitCondition(this::checkPassingCount)
                .outputKey(RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS)
                .build();

        ImageUrlFetcher imageUrlFetcher = new ImageUrlFetcher();

        // Sequential 编排
        UntypedAgent workflow = AgenticServices
                .sequenceBuilder()
                .subAgents(profileFetcher, searchScoreLoop, imageUrlFetcher)
                .outputKey(RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS)
                .errorHandler(this::handleWorkflowError)
                .build();

        log.info("推荐工作流构建完成: Sequential(ProfileFetcher → Loop(maxIter={}, target={}) → ImageUrlFetcher)",
                RecommendationConstants.MAX_ITERATIONS, RecommendationConstants.TOP_N);
        return workflow;
    }

    /** 退出条件 */
    private boolean checkPassingCount(AgenticScope scope) {
        try {
            String passing = scope.readState(RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS, "[]");
            int count = JsonUtils.parseJsonArray(passing).size();
            log.debug("退出条件检查: passingRecommendations 当前数量={}, 目标={}", count, RecommendationConstants.TOP_N);
            return count >= RecommendationConstants.TOP_N;
        } catch (Exception e) {
            log.error("退出条件检查异常", e);
        }
        return false;
    }

    /** 错误处理 */
    private ErrorRecoveryResult handleWorkflowError(ErrorContext errorContext) {
        log.error("推荐工作流 Agent [{}] 执行失败: {}", errorContext.agentName(), errorContext.exception().getMessage());

        String agentName = errorContext.agentName();
        if (RecommendationConstants.AGENT_RECOMMEND.equals(agentName)
                || RecommendationConstants.AGENT_SCORE.equals(agentName)
                || RecommendationConstants.AGENT_ACCUMULATE.equals(agentName)
                || RecommendationConstants.AGENT_EXTRACT.equals(agentName)) {
            errorContext.agenticScope().writeState(RecommendationConstants.OUTPUT_KEY_RAW_RECOMMENDATIONS, "[]");
            errorContext.agenticScope().writeState(RecommendationConstants.OUTPUT_KEY_SCORED_RESULT,
                    "{\"recommendations\":[],\"feedback\":\"搜索或评分失败，请尝试不同方向\"}");
            return ErrorRecoveryResult.retry();
        }

        return ErrorRecoveryResult.throwException();
    }
}
