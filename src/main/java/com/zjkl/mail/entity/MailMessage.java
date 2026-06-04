package com.zjkl.mail.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 信件/通知实体
 */
@Data
public class MailMessage {
    private String id;
    private String userId;
    private String tag;
    private String subject;
    private String excerpt;
    @JsonProperty("is_read")
    private boolean isRead;
    private LocalDateTime createdAt;

    /**
     * 前端需要 mail.date 展示日期字符串
     */
    @JsonProperty("date")
    public String getDate() {
        return createdAt != null ? createdAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) : null;
    }
}
