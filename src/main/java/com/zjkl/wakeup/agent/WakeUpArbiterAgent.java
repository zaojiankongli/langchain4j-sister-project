package com.zjkl.wakeup.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WakeUpArbiterAgent {

    @SystemMessage(fromResource = "prompts/wakeup-arbiter.txt")
    @UserMessage("""
            用户上下文：
            - 时段：{{timeOfDay}}（{{specialMoment}}）
            - 情绪：{{moodDescription}}（愉悦度={{moodScore}}）
            - 沉默：{{silentHours}}小时
            - 锚点：{{anchorHint}}

            候选1（侧重记忆）：
            消息：{{candidate1}}
            评分：{{score1}}/10
            理由：{{score1Reason}}

            候选2（侧重聊天）：
            消息：{{candidate2}}
            评分：{{score2}}/10
            理由：{{score2Reason}}

            候选3（侧重锚点）：
            消息：{{candidate3}}
            评分：{{score3}}/10
            理由：{{score3Reason}}
            """)
    @Agent(description = "仲裁选择最佳唤醒消息")
    String decide(@V("timeOfDay") String timeOfDay,
                  @V("specialMoment") String specialMoment,
                  @V("moodDescription") String moodDescription,
                  @V("moodScore") Double moodScore,
                  @V("silentHours") Double silentHours,
                  @V("anchorHint") String anchorHint,
                  @V("candidate1") String candidate1,
                  @V("score1") Integer score1,
                  @V("score1Reason") String score1Reason,
                  @V("candidate2") String candidate2,
                  @V("score2") Integer score2,
                  @V("score2Reason") String score2Reason,
                  @V("candidate3") String candidate3,
                  @V("score3") Integer score3,
                  @V("score3Reason") String score3Reason);
}
