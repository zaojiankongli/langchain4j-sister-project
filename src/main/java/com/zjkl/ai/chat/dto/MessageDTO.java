package com.zjkl.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zjkl.ai.chat.entity.MessageContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息响应 DTO（适配前端）
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    /**
     * 消息 ID
     */
    private String id;

    /**
     * 角色：user / ai
     */
    private String role;

    /**
     * 内容类型：text / image
     */
    private String type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 时间戳字符串
     */
    private String timestamp;

    /**
     * 原始创建时间
     */
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    /**
     * 根据 MessageContent 列表构建 type 和 content
     * 如果有图片，返回第一张图片的 URL，type='image'
     * 否则返回文本内容，type='text'
     */
    public static MessageDTO fromEntity(String id, String role, LocalDateTime createdAt, java.util.List<MessageContent> contents) {
        String type = "text";
        String content = "";

        if (contents != null && !contents.isEmpty()) {
            // 优先找图片
            for (MessageContent mc : contents) {
                if ("image".equals(mc.getType()) && mc.getUrl() != null && !mc.getUrl().isBlank()) {
                    type = "image";
                    content = mc.getUrl();
                    break;
                }
            }
            // 没有图片则找文本
            if ("text".equals(type) || content.isBlank()) {
                for (MessageContent mc : contents) {
                    if ("text".equals(mc.getType()) && mc.getText() != null && !mc.getText().isBlank()) {
                        content = mc.getText();
                        type = "text";
                        break;
                    }
                }
            }
        }

        // 格式化时间
        String timeStr = "";
        if (createdAt != null) {
            timeStr = String.format("%02d:%02d", createdAt.getHour(), createdAt.getMinute());
        }

        // role
        String frontendRole = "user".equals(role) ? "user" : "ai";

        return MessageDTO.builder()
                .id(id)
                .role(frontendRole)
                .type(type)
                .content(content)
                .timestamp(timeStr)
                .createdAt(createdAt)
                .build();
    }
}
