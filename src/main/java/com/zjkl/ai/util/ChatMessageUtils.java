package com.zjkl.ai.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatMessage 工具类
 * 
 * 提供 ChatMessage 与文本之间的转换方法
 */
public class ChatMessageUtils {

    /**
     * 将 ChatMessage 列表转换为文本
     * 
     * @param messages ChatMessage 列表
     * @return 拼接后的文本，每条消息占一行
     */
    public static String messagesToText(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        return messages.stream()
                .map(ChatMessageUtils::messageToText)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 将单条 ChatMessage 转换为文本
     * 
     * @param msg ChatMessage 对象
     * @return 转换后的文本
     */
    public static String messageToText(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        
        return switch (msg) {
            case UserMessage u -> "哥哥：" + u.singleText();
            case AiMessage a -> "妹妹：" + a.text();
            case SystemMessage s -> "[系统] " + s.text();
            default -> "";
        };
    }

    /**
     * 将 ChatMessage 列表转换为文本（带序号）
     * 
     * @param messages ChatMessage 列表
     * @return 带序号的文本
     */
    public static String messagesToTextWithIndex(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append(i + 1).append(". ").append(messageToText(messages.get(i)));
            if (i < messages.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 将 ChatMessage 列表转换为文本（过滤系统消息）
     * 
     * @param messages ChatMessage 列表
     * @return 过滤系统消息后的文本
     */
    public static String messagesToTextExcludeSystem(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        return messages.stream()
                .filter(msg -> !(msg instanceof SystemMessage))
                .map(ChatMessageUtils::messageToText)
                .collect(Collectors.joining("\n"));
    }
}
