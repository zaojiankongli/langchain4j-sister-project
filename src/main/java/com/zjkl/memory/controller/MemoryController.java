package com.zjkl.memory.controller;

import com.zjkl.common.util.DateFilterParser;
import com.zjkl.memory.domain.vo.MemoryVO;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.common.context.UserContext;
import com.zjkl.user.domain.ConversationMemory;
import com.zjkl.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 记忆查询接口（心路日记）
 */
@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
public class MemoryController {
    
    private final ConversationMemoryMapper memoryMapper;
    private final UserContext userContext;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    
    /**
     * 获取心路日记列表（分页，支持按时间筛选）
     * filter: 最近 | 2026年 | 2026.04 | 2026.03 | 更早
     */
    @GetMapping("/list")
    public Result<List<MemoryVO>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false, defaultValue = "false") boolean excludeToday
    ) {
        String userId = Objects.requireNonNull(userContext.getUserId(), "用户未登录");
        int offset = (page - 1) * size;
        
        String[] dateRange = DateFilterParser.parse(filter);
        String beginDate = dateRange[0];
        String endDate = dateRange[1];
        
        List<ConversationMemory> memories = memoryMapper.selectByUserId(userId, offset, size, beginDate, endDate, excludeToday);
        List<MemoryVO> voList = memories.stream().map(this::toMemoryVO).toList();
        return Result.success(voList);
    }
    
    /**
     * 获取记忆详情
     */
    @GetMapping("/{id}")
    public Result<MemoryVO> detail(@PathVariable Long id) {
        ConversationMemory memory = memoryMapper.selectById(id);
        
        if (memory == null) {
            return Result.error(404, "记忆不存在");
        }
        
        String currentUserId = userContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(memory.getUserId())) {
            return Result.error(403, "无权访问该记忆");
        }
        
        return Result.success(toMemoryVO(memory));
    }
    
    /**
     * 获取指定日期的记忆
     */
    @GetMapping("/date/{date}")
    public Result<MemoryVO> getByDate(@PathVariable String date) {
        String userId = Objects.requireNonNull(userContext.getUserId(), "用户未登录");
        
        try {
            LocalDate memoryDate = LocalDate.parse(date);
            ConversationMemory memory = memoryMapper.selectByUserIdAndDate(userId, memoryDate);
            
            if (memory == null) {
                return Result.error(404, "该日期没有记忆");
            }
            
            return Result.success(toMemoryVO(memory));
            
        } catch (Exception e) {
            return Result.error(400, "日期格式错误");
        }
    }
    
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
