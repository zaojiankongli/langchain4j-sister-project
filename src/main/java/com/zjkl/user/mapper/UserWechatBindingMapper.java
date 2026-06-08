package com.zjkl.user.mapper;

import com.zjkl.user.domain.UserWechatBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserWechatBindingMapper {

    UserWechatBinding findBoundByAppidAndOpenid(@Param("wechatAppid") String wechatAppid,
                                                @Param("openid") String openid);

    UserWechatBinding findBoundByUserIdAndAppid(@Param("userId") String userId,
                                                @Param("wechatAppid") String wechatAppid);

    int insert(UserWechatBinding binding);

    int updateLastLoginAt(@Param("id") Long id);
}
