package com.zjkl.ai.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * RAG 查询分析器 — langchain4j AiServices 自动解析 JSON → RouterResult
 */
interface QueryAnalyzer {

    @SystemMessage("""
            判断用户当前消息是否需要搜索历史记忆(native RAG)和/或实体关系图谱(graph RAG)，并提取可用的过滤条件。
            
            native RAG 适合：
            - 用户提及过去的事、询问之前聊过的内容
            - 提到"上次""之前""还记得""那时候""以前说过""那天"等回溯词
            - 需要叙事上下文、时间线、回忆原话或摘要

            graph RAG 适合：
            - 询问人物、关系、偏好变化、因果链
            - 例如"小王最近怎么样"、"我为什么那天烦躁"、"我和谁去过那里"
            - 需要谁/什么/因为什么这类结构化关系

            两者都不需要的场景：日常闲聊、纯粹的情绪表达、新话题开始、简单询问、感叹。
            
            输出字段要求：
            - needMemorySearch: 是否需要 native RAG
            - needGraphSearch: 是否需要 graph RAG
            - primarySource: "memory" / "graph" / "both" / null

            如果 needMemorySearch=true，请同时提取以下过滤条件（能提取几个就几个，没有就填 null）：
            - dateHint：用户提到的时间范围，如"最近一周"、"上个月"、"5月"（没有则填 null）
            - topicHint：用户提到的主题/关键词，如"摄影"、"工作"、"旅行"（没有则填 null）
            - sentimentHint：用户情绪倾向，如"开心的"、"难过的"、"焦虑的"（没有则填 null）

            如果 needMemorySearch=false，则 dateHint/topicHint/sentimentHint 全部填 null。
            """)
    RouterResult analyze(@UserMessage String userMessage);
}
