package com.zjkl.ai.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息内容项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageContent {

    /**
     * 内容类型：text / image
     */
    private String type;

    /**
     * 文本内容
     */
    private String text;

    /**
     * 图片 URL
     */
    private String url;

    /**
     * 创建文本内容
     */
    public static MessageContent text(String text) {
        return MessageContent.builder().type("text").text(text).build();
    }

    /**
     * 创建图片内容
     */
    public static MessageContent image(String url) {
        return MessageContent.builder().type("image").url(url).build();
    }
}
