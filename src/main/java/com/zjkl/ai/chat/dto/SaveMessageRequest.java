package com.zjkl.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 保存消息请求 DTO
 *
 * 用于 chat() 完成后，批量保存用户消息和 AI 回复
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMessageRequest {

    /**
     * 用户 ID
     */
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /**
     * 消息列表
     */
    @NotNull(message = "messages 不能为空")
    private List<MessageDTO> messages;
}
