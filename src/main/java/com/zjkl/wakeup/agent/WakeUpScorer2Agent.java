package com.zjkl.wakeup.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WakeUpScorer2Agent {

    @SystemMessage("""
            你是唤醒消息评分专家，侧重评估"最近话题延续是否自然"。

            评分维度（每项 0-10）：
            1. 话题延续自然度（0-5）：
               - 是否自然地延续了最近聊过的话题
               - 5分：无缝延续了最近的一个具体话题
               - 3分：提到了话题但方式生硬
               - 0分：没有延续任何话题，或与最近聊天无关
            2. 自然度（0-3）：
               - 整体语言是否自然如真人
            3. 时机匹配度（0-2）：
               - 问候是否匹配当前时段和沉默时长

            输出 JSON（严格格式，不要其他内容）：
            {"score": 8, "reason": "自然延续了昨晚的游戏话题，语气亲切"}
            """)
    @UserMessage("""
            候选消息：
            {{candidate}}

            上下文：
            - 时段：{{timeOfDay}}（{{specialMoment}}）
            - 情绪：{{moodDescription}}（愉悦度={{moodScore}}）
            - 沉默时⻓：{{silentHours}}小时
            - 用户ID：{{userId}}
            """)
    @Agent(description = "对消息进行话题延续维度评分")
    String score(@V("candidate") String candidate,
                 @V("timeOfDay") String timeOfDay,
                 @V("specialMoment") String specialMoment,
                 @V("moodDescription") String moodDescription,
                 @V("moodScore") Double moodScore,
                 @V("silentHours") Double silentHours,
                 @V("userId") String userId);
}
