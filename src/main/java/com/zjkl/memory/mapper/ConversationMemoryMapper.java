package com.zjkl.memory.mapper;


import com.zjkl.user.domain.ConversationMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 对话记忆 Mapper 接口
 */
@Mapper
public interface ConversationMemoryMapper {
    
    /**
     * 插入记忆
     */
    int insert(ConversationMemory memory);
    
    /**
     * 根据 ID 查询记忆
     */
    ConversationMemory selectById(@Param("id") Long id);
    
    /**
     * 根据用户 ID 和日期查询记忆
     */
    ConversationMemory selectByUserIdAndDate(
        @Param("userId") String userId, 
        @Param("memoryDate") LocalDate memoryDate
    );
    
    /**
     * 根据用户 ID 查询记忆列表（分页，支持可选日期范围过滤）
     */
    List<ConversationMemory> selectByUserId(
        @Param("userId") String userId,
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("beginDate") String beginDate,
        @Param("endDate") String endDate,
        @Param("excludeToday") boolean excludeToday
    );
    
    /**
     * 更新图片 URL
     */
    int updateImageUrl(
        @Param("id") Long id,
        @Param("imageUrl") String imageUrl
    );
    
    /**
     * 根据用户 ID 统计记忆数量
     */
    int countByUserId(@Param("userId") String userId);

}
