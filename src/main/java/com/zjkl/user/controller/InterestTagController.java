package com.zjkl.user.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.user.service.InterestTagGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户兴趣标签控制器
 * 🔐 认证方式：从 UserContext 获取当前用户，拒绝外部传入 userId
 */
@RestController
@RequestMapping("/api/interest-tag")
@RequiredArgsConstructor
public class InterestTagController {

    private final InterestTagGenerateService interestTagGenerateService;
    private final UserContext userContext;

    /**
     * 手动触发兴趣标签生成（测试用）
     * 调用 TagGenerator → TagScorer 工作流，异步执行，约 2 分钟超时
     * 🔐 使用 UserContext 获取当前用户，防止越权
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateTags() {
        String userId = userContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error(401, "未认证用户无法生成标签");
        }
        List<String> tags = interestTagGenerateService.generateTags(userId);
        return Result.success(Map.of(
                "userId", userId,
                "tags", tags,
                "count", tags.size()
        ));
    }
}
