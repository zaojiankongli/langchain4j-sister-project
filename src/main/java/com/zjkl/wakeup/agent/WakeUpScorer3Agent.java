package com.zjkl.wakeup.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WakeUpScorer3Agent {

    @SystemMessage("""
            你是唤醒消息评分专家，侧重评估"情绪关心是否得当"。

            评分维度（每项 0-10）：
            1. 情绪敏感性（0-5）：
               - 是否体现了对用户当前情绪状态的觉察
               - 5分：含蓄但准确地传达了关心，分寸感好
               - 3分：泛泛关心，不够具体
               - 0分：完全无视用户情绪状态
            2. 关心分寸（0-3）：
               - 是否拿捏得当，不追问不冒犯
               - 3分：分寸完美，温暖但不越界
               - 0分：追问具体事情或过于直接
            3. 时机匹配度（0-2）：
               - 问候是否匹配当前时段和沉默时长

            输出 JSON（严格格式，不要其他内容）：
            {"score": 6, "reason": "表达了关心但稍显泛泛，没有体现对具体情绪状态的觉察"}
            """)
    @UserMessage("""
            候选消息：
            {{candidate}}

            上下文：
            - 时段：{{timeOfDay}}（{{specialMoment}}）
            - 情绪：{{moodDescription}}（愉悦度={{moodScore}}）
            - 锚点信息：{{anchorHint}}
            - 沉默时⻓：{{silentHours}}小时
            - 用户ID：{{userId}}
            """)
    @Agent(description = "对消息进行情绪关心维度评分")
    String score(@V("candidate") String candidate,
                 @V("timeOfDay") String timeOfDay,
                 @V("specialMoment") String specialMoment,
                 @V("moodDescription") String moodDescription,
                 @V("moodScore") Double moodScore,
                 @V("anchorHint") String anchorHint,
                 @V("silentHours") Double silentHours,
                 @V("userId") String userId);
}
