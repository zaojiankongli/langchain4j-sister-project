package com.zjkl.user.controller;

import com.zjkl.user.domain.Result;
import com.zjkl.user.service.InterestTagGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户兴趣标签控制器
 */
@RestController
@RequestMapping("/api/interest-tag")
@RequiredArgsConstructor
public class InterestTagController {

    private final InterestTagGenerateService interestTagGenerateService;

    /**
     * 手动触发兴趣标签生成（测试用）
     * 调用 TagGenerator → TagScorer 工作流，异步执行，约 2 分钟超时
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateTags(@RequestParam(required = false) String userId) {
        List<String> tags = interestTagGenerateService.generateTags(userId);
        return Result.success(Map.of(
                "userId", userId != null ? userId : "current",
                "tags", tags,
                "count", tags.size()
        ));
    }
}
