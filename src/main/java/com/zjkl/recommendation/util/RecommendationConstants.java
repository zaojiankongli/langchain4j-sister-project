package com.zjkl.recommendation.util;

public final class RecommendationConstants {

    // === 工作流输出键 ===
    public static final String OUTPUT_KEY_PASSING_RECOMMENDATIONS = "passingRecommendations";
    public static final String OUTPUT_KEY_RAW_RECOMMENDATIONS = "rawRecommendations";
    public static final String OUTPUT_KEY_SCORED_RESULT = "scoredResult";
    public static final String OUTPUT_KEY_SEARCH_FEEDBACK = "searchFeedback";
    public static final String OUTPUT_KEY_USER_CONTEXT = "userContext";

    // === Agent 名称 ===
    public static final String AGENT_RECOMMEND = "recommend";
    public static final String AGENT_SCORE = "score";
    public static final String AGENT_ACCUMULATE = "accumulate";
    public static final String AGENT_EXTRACT = "extract";

    // === 业务常量 ===
    public static final int TOP_N = 15;
    public static final int WORKFLOW_TIMEOUT_SECONDS = 300;
    public static final double PASS_THRESHOLD = 0.6;
    public static final int MAX_ITERATIONS = 5;
    public static final int SCHEDULER_MAX_CONCURRENT = 2;
    public static final int CLEANUP_RETENTION_DAYS = 30;

    // === 来源标识 ===
    public static final String SOURCE_AGENTIC = "agentic";

    private RecommendationConstants() {}
}
