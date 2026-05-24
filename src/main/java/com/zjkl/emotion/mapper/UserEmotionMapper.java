package com.zjkl.emotion.mapper;

import com.zjkl.emotion.model.UserEmotionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserEmotionMapper {

    int insert(UserEmotionRecord record);

    List<UserEmotionRecord> selectByUserId(@Param("userId") String userId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    List<UserEmotionRecord> selectByUserIdNoLimit(@Param("userId") String userId);

    long countByUserId(@Param("userId") String userId);
}
