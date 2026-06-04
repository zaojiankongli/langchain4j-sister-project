package com.zjkl.settings.mapper;

import com.zjkl.settings.model.UserSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户设置 Mapper（MySQL 持久化）
 */
@Mapper
public interface UserSettingsMapper {

    /**
     * 查询用户设置
     */
    UserSettings findByUserId(@Param("userId") String userId);

    /**
     * 插入或更新用户设置（UPSERT）
     * @return 影响行数
     */
    int upsert(@Param("userId") String userId, @Param("settings") UserSettings settings);

    /**
     * 删除用户设置
     */
    int deleteByUserId(@Param("userId") String userId);
}
