package com.zjkl.user.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.user.domain.ConversationMemory;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserProfileMapper;
import com.zjkl.user.service.InterestTagGenerateService;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户兴趣标签生成服务实现
 */
@Slf4j
@Service
public class InterestTagGenerateServiceImpl implements InterestTagGenerateService {

    private static final int WORKFLOW_TIMEOUT_SECONDS = 120;
    private static final int MAX_TAGS_PER_USER = 10;

    private final UntypedAgent tagWorkflow;
    private final UserProfileMapper userProfileMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final Gson gson = new Gson();

    @Autowired
    @Lazy
    private InterestTagGenerateServiceImpl self;

    public InterestTagGenerateServiceImpl(UntypedAgent tagWorkflow,
                                           UserProfileMapper userProfileMapper,
                                           ConversationMemoryMapper conversationMemoryMapper) {
        this.tagWorkflow = tagWorkflow;
        this.userProfileMapper = userProfileMapper;
        this.conversationMemoryMapper = conversationMemoryMapper;
    }

    @Override
    public List<String> generateTags(String userId) {
        log.info("为用户 {} 启动兴趣标签生成工作流", userId);

        try {
            // 1. 获取用户基础信息
            User user = userProfileMapper.findUserById(userId);
            if (user == null) {
                log.warn("用户 {} 不存在，跳过标签生成", userId);
                return new ArrayList<>();
            }

            // 2. 获取历史标签（用于去重和参考）
            List<String> existingTags = userProfileMapper.findInterestTags(userId);
            String existingTagsStr = existingTags != null && !existingTags.isEmpty()
                    ? String.join("、", existingTags)
                    : "暂无";

            // 3. 从 MySQL 获取记忆列表
            List<ConversationMemory> memories = conversationMemoryMapper.selectByUserId(
                    userId, 0, 10, null, null, false);
            if (memories == null || memories.isEmpty()) {
                log.info("用户 {} 暂无记忆数据，跳过标签生成", userId);
                return new ArrayList<>();
            }

            // 4. 格式化记忆内容
            String rawMemoryContent = formatMemories(memories);
            final String memoryContent = rawMemoryContent.length() > 1000
                    ? rawMemoryContent.substring(0, 1000) + "..."
                    : rawMemoryContent;

            // 5. 调用工作流
            String result = CompletableFuture.supplyAsync(() ->
                    (String) tagWorkflow.invoke(Map.of(
                            "username", user.getUsername() != null ? user.getUsername() : "未知用户",
                            "userProfile", user.getUserProfile() != null ? user.getUserProfile() : "暂无画像",
                            "hobbies", user.getHobbies() != null ? user.getHobbies() : "暂无爱好",
                            "existingTags", existingTagsStr,
                            "memoryContent", memoryContent
                    ))
            ).orTimeout(WORKFLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();

            // 6. 解析结果
            List<String> generatedTags = parseGeneratedTags(result);

            if (generatedTags.isEmpty()) {
                log.info("用户 {} 标签生成工作流返回空结果", userId);
                return new ArrayList<>();
            }

            // 7. 保存新标签到数据库（通过 self 代理触发 @Transactional）
            self.saveGeneratedTags(userId, generatedTags, existingTags);

            log.info("用户 {} 成功生成 {} 个标签: {}", userId, generatedTags.size(), generatedTags);
            return generatedTags;

        } catch (Exception e) {
            log.error("用户 {} 标签生成失败", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 格式化记忆列表为文本
     */
    private String formatMemories(List<ConversationMemory> memories) {
        return memories.stream()
                .map(m -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("【").append(m.getMemoryDate()).append("】");
                    if (m.getTitle() != null) {
                        sb.append(m.getTitle()).append(" - ");
                    }
                    if (m.getContent() != null) {
                        sb.append(m.getContent());
                    }
                    if (m.getMood() != null) {
                        sb.append(" (心情: ").append(m.getMood()).append(")");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 解析工作流返回的标签结果
     */
    private List<String> parseGeneratedTags(String result) {
        List<String> tags = new ArrayList<>();

        try {
            if (result == null || result.isBlank()) {
                return tags;
            }

            String cleanJson = stripMarkdownJson(result);
            JsonObject jsonObj = gson.fromJson(cleanJson, JsonObject.class);

            if (jsonObj.has("scoredTags") && !jsonObj.get("scoredTags").isJsonNull()) {
                JsonArray tagsArray = jsonObj.getAsJsonArray("scoredTags");
                for (JsonElement el : tagsArray) {
                    String tag = el.getAsString().trim();
                    if (!tag.isEmpty() && tag.length() >= 2 && tag.length() <= 8) {
                        tags.add(tag);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析标签结果失败: {}", e.getMessage());
        }

        return tags;
    }

    /**
     * 保存生成的标签到数据库，超过上限时软删除最旧的标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGeneratedTags(String userId, List<String> newTags, List<String> existingTags) {
        int existingCount = existingTags != null ? existingTags.size() : 0;
        int newCount = 0;

        for (String tag : newTags) {
            // 跳过已有标签
            if (existingTags != null && existingTags.contains(tag)) {
                continue;
            }
            // 超过上限则停止
            if (existingCount + newCount >= MAX_TAGS_PER_USER) {
                break;
            }
            try {
                userProfileMapper.insertInterestTag(userId, tag);
                newCount++;
            } catch (Exception e) {
                log.warn("保存标签失败: userId={}, tag={}, error={}", userId, tag, e.getMessage());
            }
        }
    }

    /**
     * 去除 markdown 代码块包裹
     */
    private String stripMarkdownJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}