package com.zjkl.wakeup.tools;

import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.emotion.service.EmotionAnchorService;
import com.zjkl.memory.service.SummaryMemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 主动唤醒 Agent 专用工具集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpTools {

    private final SummaryMemoryService memoryService;
    private final EmotionAnchorService anchorService;
    private final ConverMessageService converMessageService;

    @Tool("搜索用户的历史对话记忆，用于生成个性化的问候消息")
    public List<String> searchMemories(@P("用户ID") String userId,
                                      @P("搜索关键词") String query,
                                      @P("返回条数") Integer limit) {
        int actualLimit = (limit != null && limit > 0) ? limit : 3;
        log.debug("Agent 调用 searchMemories: userId={}, query={}, limit={}", userId, query, actualLimit);
        List<String> memories = memoryService.searchRelevantMemories(userId, query, actualLimit);
        log.debug("searchMemories 结果: {} 条", memories.size());
        return memories;
    }

    @Tool("获取用户未聊完的话题（悬念池）")
    public List<String> getSuspenseTopics(@P("用户ID") String userId) {
        log.debug("Agent 调用 getSuspenseTopics: userId={}", userId);
        List<String> topics = anchorService.getSuspenseTopics(userId);
        log.debug("getSuspenseTopics 结果: {} 条", topics.size());
        return topics;
    }

    @Tool("获取用户最近的聊天记录，用于了解当前话题")
    public String getRecentChatContext(@P("用户ID") String userId,
                                       @P("返回最近N条消息") Integer limit) {
        int actualLimit = (limit != null && limit > 0) ? limit : 5;
        log.debug("Agent 调用 getRecentChatContext: userId={}, limit={}", userId, actualLimit);
        try {
            List<ConverMessage> messages = converMessageService.getLatestMessages(userId, actualLimit);
            if (messages.isEmpty()) {
                return "无最近聊天记录";
            }
            String result = messages.stream()
                    .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                    .map(m -> {
                        String role = "user".equals(m.getRole()) ? "你" : "妹妹";
                        String text = m.getContents() == null ? "" :
                                m.getContents().stream()
                                        .filter(c -> "text".equals(c.getType()))
                                        .map(MessageContent::getText)
                                        .collect(Collectors.joining(" "));
                        return role + ": " + text;
                    })
                    .collect(Collectors.joining("\n"));
            log.debug("getRecentChatContext 结果: {} 条消息", messages.size());
            return result;
        } catch (Exception e) {
            log.warn("获取最近聊天记录失败: userId={}", userId, e);
            return "获取聊天记录失败";
        }
    }
}
