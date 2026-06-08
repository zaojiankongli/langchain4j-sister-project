package com.zjkl.miniprogram.mapper;

import com.zjkl.miniprogram.domain.UserDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserDeviceMapper {
    UserDevice findByUserId(@Param("userId") String userId);

    UserDevice findByDeviceCode(@Param("deviceCode") String deviceCode);

    int insert(UserDevice device);

    int updateForUser(UserDevice device);
}
