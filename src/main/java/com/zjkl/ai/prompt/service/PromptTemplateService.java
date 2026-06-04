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
     * 双花括号转义占位符模式：{{variableName}}
     */
    private static final Pattern DOUBLE_BRACE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
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
     * 使用 Matcher.appendReplacement/appendTail 一次性完成所有替换，
     * 避免循环中反复创建 StringBuilder 和中间 String 对象。
     * 
     * @param template 模板内容
     * @param variables 变量
     * @return 渲染后的内容
     */
    private String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null || variables == null) {
            return template;
        }
        
        // 双花括号转义占位符（不会匹配单花括号正则，处理完后还原）
        final String ESCAPE_OPEN = "\u0000LBRACE\u0000";
        final String ESCAPE_CLOSE = "\u0000RBRACE\u0000";
        
        // 第一步：将 {{var}} 替换为临时占位符，避免被单花括号模式误匹配
        String processed = template;
        Matcher escapeMatcher = DOUBLE_BRACE_PATTERN.matcher(processed);
        StringBuilder escapeResult = new StringBuilder();
        while (escapeMatcher.find()) {
            String variableName = escapeMatcher.group(1);
            // 双花括号输出字面量 {var}，不做变量查找
            String replacement = Matcher.quoteReplacement(ESCAPE_OPEN + variableName + ESCAPE_CLOSE);
            escapeMatcher.appendReplacement(escapeResult, replacement);
        }
        escapeMatcher.appendTail(escapeResult);
        processed = escapeResult.toString();
        
        // 第二步：处理单花括号占位符：{var}
        Matcher matcher = VARIABLE_PATTERN.matcher(processed);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            
            if (value == null) {
                log.warn("模板变量未提供值：{}", variableName);
                matcher.appendReplacement(result, Matcher.quoteReplacement("{" + variableName + "}"));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
            }
        }
        matcher.appendTail(result);
        
        // 第三步：将占位符还原为字面量花括号
        return result.toString()
                .replace(ESCAPE_OPEN, "{")
                .replace(ESCAPE_CLOSE, "}");
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
