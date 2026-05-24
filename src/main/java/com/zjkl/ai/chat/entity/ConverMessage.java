package com.zjkl.ai.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话消息实体
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConverMessage {

    /**
     * 消息 ID（UUID）
     */
    private String id;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 消息内容列表
     */
    private List<MessageContent> contents;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 判断是否包含图片
     */
    public boolean hasImage() {
        if (contents == null) return false;
        return contents.stream().anyMatch(c -> "image".equals(c.getType()));
    }
}
