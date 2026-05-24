package com.zjkl.recommendation.mapper;

import com.zjkl.recommendation.entity.UserRecommendation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UserRecommendationMapper {

    /**
     * 插入推荐记录
     */
    int insert(UserRecommendation recommendation);

    /**
     * 批量插入推荐记录
     */
    int batchInsert(@Param("list") List<UserRecommendation> recommendations);

    /**
     * 查询用户指定日期的推荐
     */
    List<UserRecommendation> selectByUserIdAndDate(@Param("userId") String userId,
                                                    @Param("recommendationDate") LocalDate date);

    /**
     * 标记点击状态
     */
    int markAsClicked(@Param("id") Long id);
}
