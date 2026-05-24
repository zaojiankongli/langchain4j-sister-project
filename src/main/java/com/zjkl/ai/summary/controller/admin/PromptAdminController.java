package com.zjkl.ai.summary.controller.admin;


import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.user.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板管理 Controller
 * 
 * 提供简单的刷新接口（方案 C - 折中版）
 */
@RestController
@RequestMapping("/admin/prompts")
public class PromptAdminController {
    
    private final PromptTemplateService promptTemplateService;
    private final PromptCacheService promptCacheService;
    
    public PromptAdminController(PromptTemplateService promptTemplateService, PromptCacheService promptCacheService) {
        this.promptTemplateService = promptTemplateService;
        this.promptCacheService = promptCacheService;
    }
    
    /**
     * 刷新单个模板
     * 
     * @param templateKey 模板 Key
     * @return 结果
     */
    @PostMapping("/{templateKey}/refresh")
    public Result<String> refreshTemplate(@PathVariable String templateKey) {
        boolean success = promptTemplateService.refreshTemplate(templateKey);
        if (success) {
            return Result.success("模板刷新成功：" + templateKey);
        } else {
            return Result.error(500, "模板刷新失败：" + templateKey);
        }
    }
    
    /**
     * 刷新所有模板
     * 
     * @return 结果
     */
    @PostMapping("/refresh-all")
    public Result<Map<String, Integer>> refreshAllTemplates() {
        int count = promptTemplateService.refreshAllTemplates();
        return Result.success(Map.of("refreshed", count));
    }
    
    /**
     * 获取所有已加载的模板 Key 列表
     * 
     * @return 结果
     */
    @GetMapping("/list")
    public Result<List<String>> listTemplates() {
        return Result.success(promptCacheService.getLoadedTemplateKeys());
    }
    
    /**
     * 获取原始模板内容（用于调试）
     * 
     * @param templateKey 模板 Key
     * @return 结果
     */
    @GetMapping("/{templateKey}")
    public Result<String> getTemplate(@PathVariable String templateKey) {
        try {
            String content = promptTemplateService.getRawTemplate(templateKey);
            return Result.success(content);
        } catch (IllegalArgumentException e) {
            return Result.error(404, "模板不存在：" + templateKey);
        }
    }
}
