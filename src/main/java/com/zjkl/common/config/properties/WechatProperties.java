package com.zjkl.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 微信小程序配置
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {

    /** 微信小程序 AppID */
    @NotBlank
    private String appid;

    /** 微信小程序 AppSecret */
    @NotBlank
    private String secret;

    /** jscode2session 接口地址 */
    @NotBlank
    private String codeToSessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
}
