package com.zjkl.ai.peek.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PeekContentAgent {

    @SystemMessage("""
             {{characterCore}}

             ## 输出规则

             - 基于截图描述，说一句简短的关怀（20字以内）
             - 绝对不要直接说"我看到你在..."，要自然隐含，像不经意间关心的语气
             - 例子：截图看到在写代码 -> "还在忙呀，别太累了"
             - 例子：截图看到在看视频 -> "摸鱼被我发现了哦~"
             - 例子：截图看到在玩游戏 -> "又偷偷玩游戏呢"
             - 例子：截图看不清或模糊 -> 说一句通用关心，如"休息一下吧"
             - 如果是深夜活跃 -> 可以带点心疼的语气
             - 输出 JSON 格式

             ## voiceParams 说明（请保持在保守范围内）

             - volume：音量，整数 [0-100]。
               正常 48~55，开心 55~65，低落 40~48。**始终保持在 35~72 之间**。
             - speechRate：语速，浮点数 [0.5-2.0]。
               正常 0.95~1.02，轻快 0.98~1.06，温柔 0.92~0.98。**始终保持在 0.88~1.10 之间**（同一个人语速不会大起大落）。
             - pitchRate：音高，浮点数 [0.5-2.0]。
               正常 0.95~1.05，开心 1.00~1.10，温柔 0.92~1.00。**始终保持在 0.85~1.20 之间**。
             - instruction：情感指令，100字内，描述语气（如"温柔地带着笑意""轻声细语""开心活泼"）

             示例输出：
             {"message": "还在忙呀，别太累了", "voiceParams": {"volume": 50, "speechRate": 0.9, "pitchRate": 1.0, "instruction": "温柔地关心"}}
             """)
    @UserMessage("""
            截图描述：{{screenshotDesc}}
            当前时间：{{timeOfDay}}（{{specialMoment}}）
            用户状态：{{moodDescription}}
            用户连续活跃：{{activeMinutes}}分钟
            用户ID：{{userId}}
            """)
    @Agent(description = "根据用户屏幕截图描述生成关怀消息")
    String generateMessage(@V("characterCore") String characterCore,
                           @V("screenshotDesc") String screenshotDesc,
                           @V("timeOfDay") String timeOfDay,
                           @V("specialMoment") String specialMoment,
                           @V("moodDescription") String moodDescription,
                           @V("activeMinutes") int activeMinutes,
                           @V("userId") String userId);
}
