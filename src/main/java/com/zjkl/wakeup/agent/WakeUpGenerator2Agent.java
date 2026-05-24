package com.zjkl.wakeup.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WakeUpGenerator2Agent {

    @SystemMessage(fromResource = "prompts/wakeup-generator-2.txt")
    @UserMessage("""
            当前是{{timeOfDay}}（{{specialMoment}}），
            用户情绪：{{moodDescription}}（愉悦度={{moodScore}}），
            用户沉默约{{silentHours}}小时，
            锚点信息：{{anchorHint}}，
            用户ID：{{userId}}
            """)
    @Agent(description = "侧重最近聊天的问候生成")
    String generate(@V("timeOfDay") String timeOfDay,
                    @V("specialMoment") String specialMoment,
                    @V("moodDescription") String moodDescription,
                    @V("moodScore") Double moodScore,
                    @V("silentHours") Double silentHours,
                    @V("anchorHint") String anchorHint,
                    @V("userId") String userId);
}
