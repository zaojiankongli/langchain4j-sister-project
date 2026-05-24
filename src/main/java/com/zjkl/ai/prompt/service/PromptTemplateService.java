package com.zjkl.ai.prompt.service;

import com.zjkl.memory.service.PromptCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板服务
 * 
 * 提供模板获取和变量渲染功能
 */
@Service
@Slf4j
public class PromptTemplateService {

    private final PromptCacheService cacheService;
    
    public PromptTemplateService(PromptCacheService cacheService) {
        this.cacheService = cacheService;
    }
    
    /**
     * 变量占位符模式：{variableName}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    /**
     * 获取模板并渲染变量
     * 
     * @param templateKey 模板 Key（如 "summary-full"）
     * @param variables 变量 Map
     * @return 渲染后的模板
     */
    public String render(String templateKey, Map<String, Object> variables) {
        String template = cacheService.getTemplate(templateKey);
        return renderTemplate(template, variables);
    }
    
    /**
     * 获取原始模板（不渲染）
     * 
     * @param templateKey 模板 Key
     * @return 模板内容
     */
    public String getRawTemplate(String templateKey) {
        return cacheService.getTemplate(templateKey);
    }
    
    /**
     * 渲染模板
     * 
     * @param template 模板内容
     * @param variables 变量
     * @return 渲染后的内容
     */
    private String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null || variables == null) {
            return template;
        }
        
        StringBuilder result = new StringBuilder(template);
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        // 从后往前替换，避免索引偏移
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            
            if (value == null) {
                log.warn("模板变量未提供值：{}", variableName);
                value = "{" + variableName + "}";  // 保留原样
            }
            
            // 处理特殊转义：{{ 和 }} 表示字面量的花括号
            String placeholder = "{" + variableName + "}";
            String escapedPlaceholder = "{{" + variableName + "}}";
            
            // 如果模板中是双花括号，替换为单花括号（用于 JSON 输出）
            if (template.contains(escapedPlaceholder)) {
                result = new StringBuilder(result.toString().replace(escapedPlaceholder, 
                    value instanceof String ? "{" + value + "}" : value.toString()));
            } else {
                result = new StringBuilder(result.toString().replace(placeholder, 
                    value.toString()));
            }
        }
        
        return result.toString();
    }
    
    /**
     * 刷新模板缓存
     * 
     * @param templateKey 模板 Key
     * @return 是否成功
     */
    public boolean refreshTemplate(String templateKey) {
        return cacheService.refreshTemplate(templateKey);
    }
    
    /**
     * 刷新所有模板缓存
     * 
     * @return 刷新的模板数量
     */
    public int refreshAllTemplates() {
        return cacheService.refreshAllTemplates();
    }
}
