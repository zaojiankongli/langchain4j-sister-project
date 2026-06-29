package com.zjkl.memory.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.memory.domain.vo.MemoryVO;
import com.zjkl.memory.service.MemoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 记忆查询接口。
 */
@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryQueryService memoryQueryService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/list")
    public Result<List<MemoryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "false") boolean excludeToday) {
        return endpointMetrics.recordResult("web", "memory.list", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            List<MemoryVO> voList = memoryQueryService.listMemories(userId, page, size, filter, excludeToday);
            return Result.success(voList);
        });
    }

    @GetMapping("/{id}")
    public Result<MemoryVO> detail(@PathVariable Long id) {
        return endpointMetrics.recordResult("web", "memory.detail", () -> {
            String currentUserId = userContext.getUserId();
            if (currentUserId == null) {
                return Result.unauthorized("请先登录");
            }
            try {
                MemoryVO vo = memoryQueryService.getMemoryDetail(id, currentUserId);
                return Result.success(vo);
            } catch (BusinessException e) {
                return Result.error(e.getCode(), e.getMessage());
            }
        });
    }

    @GetMapping("/date/{date}")
    public Result<MemoryVO> getByDate(@PathVariable String date) {
        return endpointMetrics.recordResult("web", "memory.by_date", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            try {
                MemoryVO vo = memoryQueryService.getMemoryByDate(userId, date);
                return Result.success(vo);
            } catch (BusinessException e) {
                return Result.error(e.getCode(), e.getMessage());
            }
        });
    }
}
