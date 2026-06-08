package com.zjkl.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信小程序登录请求
 */
public record WxLoginRequest(
    @NotBlank(message = "微信 code 不能为空")
    String code
) {}
