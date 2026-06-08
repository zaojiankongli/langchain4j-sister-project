package com.zjkl.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String wxOpenid;
    private String wxUnionid;
    private String wxNickname;
    private String wxAvatarUrl;
    private LocalDateTime wxBoundAt;
    private String username;
    private String avatarUrl;
    private String backgroundUrl;
    private Integer gender;
    private String hobbies;
    private String userProfile;
    private Integer aiType;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate birthday;

    /**
     * 判断是否需要完善资料（aiType 为 null 表示未选择 AI 身份）
     */
    public boolean requiresProfileComplete() {
        return aiType == null || username == null;
    }
}
