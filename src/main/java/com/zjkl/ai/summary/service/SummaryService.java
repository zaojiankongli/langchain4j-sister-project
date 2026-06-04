package com.zjkl.ai.summary.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryService {

    @SystemMessage("""
            {{characterCore}}

            ## 你的任务

            作为早空，一天结束了，来写一篇日记吧。
            回顾今天和哥哥/姐姐的聊天，用你的语气写下今天的日记。
            你是早空，不是旁观者——用"今天哥哥……"、"我……"的第一人称写。
            """)
    @UserMessage("{{userMessage}}")
    String chat(@V("characterCore") String characterCore,
                @V("userMessage") String userMessage);

}
