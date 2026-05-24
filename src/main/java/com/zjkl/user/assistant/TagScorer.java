package com.zjkl.user.assistant;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 对候选标签进行评分和质量筛选
 */
public interface TagScorer {

    @SystemMessage(fromResource = "prompts/interest-tag-score-prompt.txt")
    @UserMessage("""
            用户名: {{username}}

            待评分标签:
            {{candidateTags}}

            请评估每个标签的质量，计算综合得分，筛选出最合适的标签。
            """)
    @Agent(value = "评估候选标签质量，筛选高质量标签并输出最终结果", outputKey = "scoredTags")
    String scoreTags(@V("username") String username,
                    @V("candidateTags") String candidateTags);
}