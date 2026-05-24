package com.zjkl.wakeup.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WakeUpScorer1Agent {

    @SystemMessage("""
            你是唤醒消息评分专家，侧重评估"历史记忆引用是否精准自然"。

            评分维度（每项 0-10）：
            1. 记忆引用精准度（0-5）：
               - 是否真正引用了用户的具体历史记忆内容
               - 5分：准确引用了一条真实的记忆内容
               - 3分：提到了记忆但比较模糊
               - 0分：没有引用任何记忆，或编造了记忆
            2. 自然度（0-3）：
               - 引用记忆的方式是否自然，不生硬
            3. 时机匹配度（0-2）：
               - 问候是否匹配当前时段和情绪

            输出 JSON（严格格式，不要其他内容）：
            {"score": 7, "reason": "引用了昨天聊到的工作话题，语气自然，但稍显笼统"}
            """)
    @UserMessage("""
            候选消息：
            {{candidate}}

            上下文：
            - 时段：{{timeOfDay}}（{{specialMoment}}）
            - 情绪：{{moodDescription}}（愉悦度={{moodScore}}）
            - 用户ID：{{userId}}
            """)
    @Agent(description = "对消息进行记忆引用维度评分")
    String score(@V("candidate") String candidate,
                 @V("timeOfDay") String timeOfDay,
                 @V("specialMoment") String specialMoment,
                 @V("moodDescription") String moodDescription,
                 @V("moodScore") Double moodScore,
                 @V("userId") String userId);
}
