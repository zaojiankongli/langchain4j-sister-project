package com.zjkl.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 完善个人资料请求
 */
public record CompleteProfileRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在 2-20 之间")
    String username,

    Integer gender,
    
    /**
     * AI 身份（必填）
     * 1-哥哥，2-妹妹，3-姐姐，4-弟弟，5-青梅，6-竹马
     */
    Integer aiType,
    
    /**
     * 爱好列表（支持数组和逗号分隔字符串两种格式）
     */
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    List<String> hobbies,
    
    String birthday,
    
    /**
     * 头像 URL
     */
    String avatarUrl
) {}
