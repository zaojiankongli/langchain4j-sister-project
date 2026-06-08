package com.zjkl.anchor.mapper;

import com.zjkl.anchor.model.AnchorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 锚点事件 Mapper（简化版）
 */
@Mapper
public interface AnchorMapper {

    int insert(AnchorEvent event);

    /**
     * 更新锚点事件的结束字段
     * 乐观条件：只更新 end_time IS NULL 的记录，防止重复更新
     */
    int updateEndFields(AnchorEvent event);

    AnchorEvent selectById(@Param("id") Long id);

    List<AnchorEvent> selectRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 分页查询用户的锚点事件
     */
    List<AnchorEvent> selectByUserIdPaged(
        @Param("userId") String userId,
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("beginDate") String beginDate,
        @Param("endDate") String endDate
    );

    /**
     * 轻量查询：只返回最近N条已结束事件的end_type字符串
     */
    List<String> selectRecentEndTypes(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 查询用户最近的负面结束事件的触发原因（悬念池）
     */
    List<String> selectRecentNegativeTopics(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 查询用户性格演变事件（evolution 接口）
     */
    List<Map<String, Object>> selectEvolutionByUserId(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 关闭超过最大持续时长的未结束锚点事件（服务重启恢复时使用）
     */
    int closeStaleEvents(@Param("maxDurationMinutes") int maxDurationMinutes);

    /**
     * 查询所有未关闭的锚点事件（end_time IS NULL），用于服务重启时重建 activeEventIds 映射
     */
    List<AnchorEvent> selectOpenEvents();
}
