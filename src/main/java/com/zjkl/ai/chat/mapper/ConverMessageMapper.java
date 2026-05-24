package com.zjkl.ai.chat.mapper;

import com.zjkl.ai.chat.entity.ConverMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ConverMessageMapper {

    /**
     * 插入单条消息
     */
    int insert(ConverMessage message);

    /**
     * 批量插入消息
     */
    int batchInsert(@Param("list") List<ConverMessage> messages);

    /**
     * 根据 ID 查询（用于懒加载替换）
     */
    ConverMessage selectById(@Param("id") String id);

    /**
     * 查询用户的消息历史（分页）
     */
    List<ConverMessage> selectByUserId(@Param("userId") String userId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 查询用户的消息历史（按时间范围）
     */
    List<ConverMessage> selectByUserIdAndTimeRange(@Param("userId") String userId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询用户最近的 N 条消息
     */
    List<ConverMessage> selectLatestByUserId(@Param("userId") String userId,
                                             @Param("limit") int limit);

    /**
     * 删除用户的所有消息
     */
    int deleteByUserId(@Param("userId") String userId);

    /**
     * 查询用户会话摘要（按日期分组）
     */
    List<Map<String, Object>> selectSessionPreviews(@Param("userId") String userId, @Param("limit") int limit);
}
