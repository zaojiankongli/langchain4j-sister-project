package com.zjkl.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户微信绑定关系。
 *
 * <p>微信身份绑定的 source of truth 是 user_wechat_bindings 表，users.wx_* 仅作为历史兼容字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWechatBinding {

    private Long id;
    private String userId;
    private String wechatAppid;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String bindStatus;
    private LocalDateTime boundAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
