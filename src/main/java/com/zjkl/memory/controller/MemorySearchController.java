package com.zjkl.memory.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.memory.service.SummaryMemoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 记忆搜索接口。
 */
@RestController
@RequestMapping("/api/memory/search")
@Validated
public class MemorySearchController {

    private final SummaryMemoryService summaryMemoryService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    private final EndpointMetrics endpointMetrics;

    public MemorySearchController(SummaryMemoryService summaryMemoryService,
                                  UserContext userContext,
                                  RateLimiter rateLimiter,
                                  EndpointMetrics endpointMetrics) {
        this.summaryMemoryService = summaryMemoryService;
        this.userContext = userContext;
        this.rateLimiter = rateLimiter;
        this.endpointMetrics = endpointMetrics;
    }

    @GetMapping
    public Result<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return endpointMetrics.recordResult("web", "memory.search", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            if (!rateLimiter.tryAcquire("rate:memsearch:" + userId, 10, 60_000L)) {
                return Result.rateLimited("搜索过于频繁，请稍后再试");
            }
            List<String> results = summaryMemoryService.searchRelevantMemories(userId, query, limit);
            return Result.success(Map.of("userId", userId, "query", query, "results", results, "count", results.size()));
        });
    }

    @GetMapping("/by-date")
    public Result<Map<String, Object>> searchByDate(
            @RequestParam String query,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return endpointMetrics.recordResult("web", "memory.search_by_date", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            if (!rateLimiter.tryAcquire("rate:memsearch:" + userId, 10, 60_000L)) {
                return Result.rateLimited("搜索过于频繁，请稍后再试");
            }
            List<String> results = summaryMemoryService.searchMemoriesByDateRange(userId, query, startDate, endDate, limit);
            return Result.success(Map.of(
                    "userId", userId,
                    "query", query,
                    "startDate", startDate,
                    "endDate", endDate,
                    "results", results,
                    "count", results.size()
            ));
        });
    }
}
