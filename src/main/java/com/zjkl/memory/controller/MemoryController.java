package com.zjkl.memory.controller;

import com.zjkl.memory.domain.vo.MemoryVO;
import com.zjkl.memory.service.MemoryQueryService;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记忆查询接口（心路日记）
 */
@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
public class MemoryController {
    
    private final MemoryQueryService memoryQueryService;
    private final UserContext userContext;
    
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
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        // 分页边界校验由 service 层统一处理（MemoryQueryService.MAX_PAGE_SIZE）
        List<MemoryVO> voList = memoryQueryService.listMemories(userId, page, size, filter, excludeToday);
        return Result.success(voList);
    }
    
    /**
     * 获取记忆详情
     */
    @GetMapping("/{id}")
    public Result<MemoryVO> detail(@PathVariable Long id) {
        String currentUserId = userContext.getUserId();
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }
        try {
            MemoryVO vo = memoryQueryService.getMemoryDetail(id, currentUserId);
            return Result.success(vo);
        } catch (com.zjkl.common.exception.BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }
    
    /**
     * 获取指定日期的记忆
     */
    @GetMapping("/date/{date}")
    public Result<MemoryVO> getByDate(@PathVariable String date) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        try {
            MemoryVO vo = memoryQueryService.getMemoryByDate(userId, date);
            return Result.success(vo);
        } catch (com.zjkl.common.exception.BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }
}
