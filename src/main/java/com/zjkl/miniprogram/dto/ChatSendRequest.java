package com.zjkl.miniprogram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 500, message = "消息内容不能超过500个字符")
        String content,

        String messageType,

        String imageUrl
) {}
