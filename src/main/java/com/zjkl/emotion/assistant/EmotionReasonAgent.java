package com.zjkl.emotion.assistant;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionReasonAgent {

    @SystemMessage(fromResource = "prompts/emotion-reason-prompt.txt")
    @UserMessage("""
            当前时间: {{time}}
            情绪标签: {{moodLabel}}
            PAD数值: 愉悦度={{pleasure}}, 唤醒度={{arousal}}, 支配感={{dominance}}

            最近聊天记录:
            {{chatHistory}}

            请推测哥哥/姐姐此刻的情绪状态原因。
            """)
    @Agent("根据当前情绪 PAD 值和聊天记录，推测情绪状态的原因")
    String generateReason(@V("time") String time,
                          @V("moodLabel") String moodLabel,
                          @V("pleasure") String pleasure,
                          @V("arousal") String arousal,
                          @V("dominance") String dominance,
                          @V("chatHistory") String chatHistory);
}
