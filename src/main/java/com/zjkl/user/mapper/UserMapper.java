package com.zjkl.user.mapper;


import com.zjkl.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    
    User findByEmail(@Param("email") String email);
    
    User findById(@Param("id") String id);
    
    int insert(User user);
    
    int update(User user);
    
    int updateLastActiveAt(@Param("id") String id);
}
