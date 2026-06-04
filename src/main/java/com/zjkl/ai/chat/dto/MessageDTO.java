package com.zjkl.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zjkl.ai.chat.entity.MessageContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
     * 图文消息时的文本内容（type=image 时保留文本，不丢弃）
     */
    private String text;

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
     * 如果有图片，返回第一张图片的 URL，type='image'，同时保留文本在 text 字段
     * 否则返回文本内容，type='text'
     */
    public static MessageDTO fromEntity(String id, String role, LocalDateTime createdAt, java.util.List<MessageContent> contents) {
        String type = "text";
        String content = "";
        String text = null;

        if (contents != null && !contents.isEmpty()) {
            // 先提取文本内容
            for (MessageContent mc : contents) {
                if ("text".equals(mc.getType()) && mc.getText() != null && !mc.getText().isBlank()) {
                    text = mc.getText();
                    break;
                }
            }
            // 再查找图片
            String imageUrl = null;
            for (MessageContent mc : contents) {
                if ("image".equals(mc.getType()) && mc.getUrl() != null && !mc.getUrl().isBlank()) {
                    imageUrl = mc.getUrl();
                    break;
                }
            }
            // 有图片则为图文消息：content 存图片 URL，text 存文本
            if (imageUrl != null) {
                type = "image";
                content = imageUrl;
            } else if (text != null) {
                type = "text";
                content = text;
            }
        }

        // 格式化时间：发完整 ISO 字符串（始终含秒），前端自己分割日期和时分
        String timeStr = "";
        if (createdAt != null) {
            timeStr = createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        // role
        String frontendRole = "user".equals(role) ? "user" : "ai";

        return MessageDTO.builder()
                .id(id)
                .role(frontendRole)
                .type(type)
                .content(content)
                .text(text)
                .timestamp(timeStr)
                .createdAt(createdAt)
                .build();
    }
}
