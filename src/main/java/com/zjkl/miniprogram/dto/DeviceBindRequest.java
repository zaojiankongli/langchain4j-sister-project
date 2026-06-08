package com.zjkl.miniprogram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceBindRequest(
        @NotBlank(message = "设备码不能为空")
        @Size(max = 64, message = "设备码不能超过64个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备码只能包含字母、数字、下划线或短横线")
        String deviceCode,

        @Size(max = 100, message = "设备昵称不能超过100个字符")
        String nickname
) {}
