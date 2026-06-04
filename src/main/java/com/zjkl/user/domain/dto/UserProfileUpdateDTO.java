package com.zjkl.user.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料更新 DTO
 */
@Data
public class UserProfileUpdateDTO {

    @Size(max = 50, message = "用户名不能超过50个字符")
    private String username;

    private LocalDate birthday;

    @Size(max = 500, message = "兴趣爱好不能超过500个字符")
    private String hobbies;
}
