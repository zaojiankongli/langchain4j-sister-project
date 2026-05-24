package com.zjkl.user.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料更新 DTO
 */
@Data
public class UserProfileUpdateDTO {
    
    private String username;
    private LocalDate birthday;
    private String hobbies;
}
