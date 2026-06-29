package com.zjkl.anchor.controller;

import com.zjkl.anchor.service.AnchorService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.common.util.DateFilterParser;
import com.zjkl.memory.domain.vo.MemoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 锚点事件查询接口。
 */
@RestController
@RequestMapping("/api/ai/anchor")
@RequiredArgsConstructor
public class AnchorController {

    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final AnchorService anchorService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/list")
    public Result<List<MemoryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String filter) {
        return endpointMetrics.recordResult("web", "anchor.list", () -> {
            int safePage = Math.max(1, Math.min(page, MAX_PAGE));
            int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            int offset = (safePage - 1) * safeSize;

            String[] dateRange = DateFilterParser.parse(filter);
            String beginDate = dateRange[0];
            String endDate = dateRange[1];

            List<MemoryVO> voList = anchorService.getMilestones(userId, offset, safeSize, beginDate, endDate);
            return Result.success(voList);
        });
    }
}
