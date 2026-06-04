package com.zjkl.memory.service;

import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.util.DateFilterParser;
import com.zjkl.memory.domain.vo.MemoryVO;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.user.domain.ConversationMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 记忆查询服务（心路日记）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final ConversationMemoryMapper memoryMapper;

    /**
     * 获取心路日记列表（分页，支持按时间筛选）
     *
     * @param userId      用户ID
     * @param page        页码
     * @param size        每页大小
     * @param filter      筛选条件：最近 | 2026年 | 2026.04 | 2026.03 | 更早
     * @param excludeToday 是否排除今天
     * @return 记忆列表
     */
    @Transactional(readOnly = true)
    public List<MemoryVO> listMemories(String userId, int page, int size, String filter, boolean excludeToday) {
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        page = Math.max(1, Math.min(page, MAX_PAGE));
        int offset = (page - 1) * size;

        String[] dateRange = DateFilterParser.parse(filter);
        String beginDate = dateRange[0];
        String endDate = dateRange[1];

        List<ConversationMemory> memories = memoryMapper.selectByUserId(userId, offset, size, beginDate, endDate, excludeToday);
        return memories.stream().map(this::toMemoryVO).toList();
    }

    /**
     * 获取记忆详情
     *
     * @param id            记忆ID
     * @param currentUserId 当前用户ID
     * @return 记忆VO
     * @throws BusinessException 如果记忆不存在或无权访问
     */
    @Transactional(readOnly = true)
    public MemoryVO getMemoryDetail(Long id, String currentUserId) {
        ConversationMemory memory = memoryMapper.selectById(id);

        if (memory == null) {
            throw new BusinessException(404, "记忆不存在");
        }

        if (currentUserId == null || !currentUserId.equals(memory.getUserId())) {
            throw new BusinessException(403, "无权访问该记忆");
        }

        return toMemoryVO(memory);
    }

    /**
     * 获取指定日期的记忆
     *
     * @param userId 用户ID
     * @param date   日期字符串（yyyy-MM-dd格式）
     * @return 记忆VO
     * @throws BusinessException 如果日期格式错误或该日期没有记忆
     */
    @Transactional(readOnly = true)
    public MemoryVO getMemoryByDate(String userId, String date) {
        try {
            LocalDate memoryDate = LocalDate.parse(date);
            ConversationMemory memory = memoryMapper.selectByUserIdAndDate(userId, memoryDate);

            if (memory == null) {
                throw new BusinessException(404, "该日期没有记忆");
            }

            return toMemoryVO(memory);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误");
        }
    }

    /**
     * 将实体转换为 VO
     */
    private MemoryVO toMemoryVO(ConversationMemory memory) {
        MemoryVO vo = new MemoryVO();
        vo.setId(memory.getId());
        vo.setQuote(memory.getTitle());
        vo.setTitle(memory.getTitle());
        vo.setDesc(memory.getContent());
        vo.setContent(memory.getContent());
        vo.setMood(memory.getMood());
        vo.setType("journal");
        vo.setImageUrl(memory.getImageUrl());
        if (memory.getMemoryDate() != null) {
            vo.setDate(memory.getMemoryDate().format(DATE_FORMATTER));
        }
        return vo;
    }
}
