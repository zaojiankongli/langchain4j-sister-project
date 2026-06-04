package com.zjkl.memory.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.memory.service.SummaryMemoryService;
import com.zjkl.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记忆搜索测试接口
 */
@RestController
@RequestMapping("/api/memory/search")
@Validated
public class MemorySearchController {

    private final SummaryMemoryService summaryMemoryService;
    private final UserContext userContext;

    public MemorySearchController(SummaryMemoryService summaryMemoryService, UserContext userContext) {
        this.summaryMemoryService = summaryMemoryService;
        this.userContext = userContext;
    }

    @GetMapping
    public Result<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        List<String> results = summaryMemoryService.searchRelevantMemories(userId, query, limit);
        return Result.success(Map.of("userId", userId, "query", query, "results", results, "count", results.size()));
    }

    @GetMapping("/by-date")
    public Result<Map<String, Object>> searchByDate(
            @RequestParam String query,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        List<String> results = summaryMemoryService.searchMemoriesByDateRange(userId, query, startDate, endDate, limit);
        return Result.success(Map.of("userId", userId, "query", query,
                "startDate", startDate, "endDate", endDate,
                "results", results, "count", results.size()));
    }
}
