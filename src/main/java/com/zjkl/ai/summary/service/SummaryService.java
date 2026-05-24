package com.zjkl.ai.summary.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryService {

    @SystemMessage("你是一位擅长捕捉情感流动的传记作家。你的核心任务是将“旧摘要”（长期记忆）与“新聊天记录”（短期记忆）融合，生成一份连贯的、日记体的“新摘要”")
    @UserMessage("{{userMessage}}")
    String chat(@V("userMessage") String userMessage);

}
