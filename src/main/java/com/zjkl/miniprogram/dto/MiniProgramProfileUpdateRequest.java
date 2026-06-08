package com.zjkl.miniprogram.dto;

public record MiniProgramProfileUpdateRequest(
        String nickname,
        String username,
        String avatarUrl,
        String backgroundUrl,
        String mood,
        String accountStatus
) {}
