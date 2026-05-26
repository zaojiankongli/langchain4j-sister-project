package com.zjkl.memory.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.common.config.properties.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Prompt 模板缓存
 */
@Service
@Slf4j
public class PromptCacheService {
    
    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;

    private static final String REDIS_KEY_PREFIX = "prompt:template:";
    
    private static final String RESOURCE_PATH = "classpath:prompts/**/*.txt";
    
    private final Set<String> loadedTemplateKeys = ConcurrentHashMap.newKeySet();
    
    private Cache<String, String> localCache;
    
    public PromptCacheService(StringRedisTemplate stringRedisTemplate, AppProperties appProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.appProperties = appProperties;
    }
    
    @PostConstruct
    public void init() {
        int cacheTtl = appProperties.getPromptCacheTtl();
        // 延迟初始化 Caffeine 缓存
        this.localCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(cacheTtl, TimeUnit.SECONDS)
                .build();

        log.info("开始预加载 Prompt 模板（Caffeine TTL={}s）...", cacheTtl);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(RESOURCE_PATH);
            
            if (resources == null || resources.length == 0) {
                log.warn("未找到任何 Prompt 模板文件，路径：{}", RESOURCE_PATH);
                return;
            }
            
            int count = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }
                
                String key = extractKeyFromResource(resource, filename);
                String content;
                try (var is = resource.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                
                // 存入 Redis
                String redisKey = REDIS_KEY_PREFIX + key;
        stringRedisTemplate.opsForValue().set(redisKey, content, Duration.ofSeconds(appProperties.getPromptCacheTtl()));
                
                // 存入本地缓存
                localCache.put(key, content);
                
                // 记录已加载的 Key
                loadedTemplateKeys.add(key);
                
                count++;
                log.debug("加载模板：{} ({} 字节)", key, content.length());
            }
            
            log.info("Prompt 模板预加载完成，共加载 {} 个模板", count);
            
        } catch (IOException e) {
            log.error("Prompt 模板预加载失败", e);
            throw new RuntimeException("Prompt 模板预加载失败", e);
        }
    }
    
    /**
     * 查模板
     */
    public String getTemplate(String key) {
        String content = localCache.getIfPresent(key);
        if (content != null) {
            log.debug("命中本地缓存：{}", key);
            return content;
        }
        
        String redisKey = REDIS_KEY_PREFIX + key;
        content = stringRedisTemplate.opsForValue().get(redisKey);
        if (content != null) {
            log.debug("命中 Redis 缓存：{}", key);
            localCache.put(key, content);
            return content;
        }
        
        log.warn("缓存未命中，从文件加载：{}", key);
        content = loadFromFile(key);
        if (content != null) {
            // 回填 Redis 和本地缓存
            stringRedisTemplate.opsForValue().set(redisKey, content, Duration.ofSeconds(appProperties.getPromptCacheTtl()));
            localCache.put(key, content);
            loadedTemplateKeys.add(key);
            return content;
        }
        
        throw new IllegalArgumentException("未找到 Prompt 模板：" + key);
    }
    
    /** 从文件加载 */
    private String loadFromFile(String key) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:prompts/" + key + ".txt");
            if (!resource.exists()) {
                return null;
            }
            try (var is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("从文件加载模板失败：{}", key, e);
            return null;
        }
    }
    
    /** 提取 key */
    private String extractKeyFromResource(Resource resource, String filename) {
        try {
            // 获取资源的 URL 路径
            String urlPath = resource.getURL().getPath();
            // URL 解码（处理中文路径等）
            urlPath = java.net.URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
            int promptsIndex = urlPath.lastIndexOf("prompts/");
            if (promptsIndex >= 0) {
                String relativePath = urlPath.substring(promptsIndex + "prompts/".length());
                // 去掉 .txt 后缀
                return relativePath.replace(".txt", "");
            }
            // 降级：直接用文件名（不带路径）
            return filename.replace(".txt", "");
        } catch (Exception e) {
            log.warn("提取 resource key 失败，使用文件名代替: {}", filename, e);
            return filename.replace(".txt", "");
        }
    }
    
    /**
     * 刷新模板缓存
     */
    public boolean refreshTemplate(String key) {
        log.info("刷新模板：{}", key);
        
        String content = loadFromFile(key);
        if (content == null) {
            log.error("刷新模板失败：文件不存在 - {}", key);
            return false;
        }
        
        // 更新 Redis
        String redisKey = REDIS_KEY_PREFIX + key;
        stringRedisTemplate.opsForValue().set(redisKey, content, Duration.ofSeconds(appProperties.getPromptCacheTtl()));
        
        // 更新本地缓存
        localCache.put(key, content);
        loadedTemplateKeys.add(key);
        
        log.info("模板刷新成功：{}", key);
        return true;
    }
    
    /** 刷新所有 */
    public int refreshAllTemplates() {
        log.info("刷新所有模板...");
        int count = 0;
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(RESOURCE_PATH);
            
            if (resources == null) {
                return 0;
            }
            
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }
                
                String key = extractKeyFromResource(resource, filename);
                if (refreshTemplate(key)) {
                    count++;
                }
            }
            
            log.info("刷新完成，共刷新 {} 个模板", count);
            
        } catch (IOException e) {
            log.error("刷新所有模板失败", e);
            throw new RuntimeException("刷新所有模板失败", e);
        }
        
        return count;
    }
    
    /** 获取已加载 Key */
    public List<String> getLoadedTemplateKeys() {
        return new java.util.ArrayList<>(loadedTemplateKeys);
    }
    
    /** 清除缓存 */
    public void clearCache(String key) {
        localCache.invalidate(key);
        String redisKey = REDIS_KEY_PREFIX + key;
        stringRedisTemplate.delete(redisKey);
        log.debug("已清除模板缓存：{}", key);
    }
}
