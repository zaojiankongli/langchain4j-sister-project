package com.zjkl.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 微信绑定邮箱请求
 */
public record BindEmailRequest(
    @NotBlank(message = "绑定令牌不能为空")
    String bindToken,

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Pattern(regexp = "^\\S+@\\S+\\.\\S+$", message = "邮箱不能包含空格")
    String email,

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码长度必须为6位")
    String code
) {}
