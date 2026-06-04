package com.zjkl.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送验证码请求
 */
public record SendCodeRequest(
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Pattern(regexp = "^\\S+@\\S+\\.\\S+$", message = "邮箱不能包含空格")
    String email
) {}
