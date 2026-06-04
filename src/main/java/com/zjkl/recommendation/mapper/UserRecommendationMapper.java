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
     * 根据 ID 查询推荐
     */
    UserRecommendation selectById(@Param("id") Long id);

    /**
     * 标记点击状态
     */
    int markAsClicked(@Param("id") Long id);

    /**
     * 删除指定日期之前的推荐记录
     */
    int deleteOlderThan(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * 删除某用户指定日期的推荐记录（重生成时使用）
     */
    int deleteByUserAndDate(@Param("userId") String userId, @Param("recommendationDate") LocalDate date);
}
