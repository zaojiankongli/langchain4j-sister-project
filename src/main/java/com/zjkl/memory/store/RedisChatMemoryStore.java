package com.zjkl.memory.store;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.memory.constant.MemoryRedisKeys;
import com.zjkl.memory.service.SummaryMemoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Redis 聊天记忆存储
 * 
 * 实现 ChatMemoryStore 接口，负责存储和压缩聊天历史
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {
    
    private final StringRedisTemplate redisTemplate;
    private final UserActivityTracker userActivityTracker;
    private final SummaryMemoryService summaryMemoryService;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(MemoryRedisKeys.HISTORY_KEY + memoryId);
        return Optional.ofNullable(json)
                .filter(s -> !s.trim().isEmpty())
                .map(ChatMessageDeserializer::messagesFromJson)
                .orElseGet(Collections::emptyList);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null || messages == null || messages.isEmpty()) {
            return;
        }

        String memoryIdStr = memoryId.toString();
        
        // 1. 记录用户活跃时间
        userActivityTracker.recordActivity(memoryIdStr);
        
        // 2. 检查是否需要压缩（不依赖锁，只做快速判断）
        if (shouldCompact(memoryId, messages)) {
            log.info("用户 {} 触发摘要生成，当前消息数：{}", memoryIdStr, messages.size());
            
            List<ChatMessage> messagesSnapshot = new ArrayList<>(messages);
            
            String json = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(MemoryRedisKeys.HISTORY_KEY + memoryId, json, MemoryRedisKeys.EXPIRATION_1_DAY);
            
            summaryMemoryService.generateSummaryAsync(memoryIdStr, messagesSnapshot);
        } else {
            String json = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(MemoryRedisKeys.HISTORY_KEY + memoryId, json, MemoryRedisKeys.EXPIRATION_1_DAY);
        }
    }
    
    private boolean shouldCompact(Object memoryId, List<ChatMessage> messages) {
        int currentSize = messages.size();
        String memoryIdStr = memoryId.toString();
        
        if (currentSize >= MemoryRedisKeys.SUMMARY_THRESHOLD) {
            log.debug("用户 {} 消息数 {} >= {}，触发压缩", memoryIdStr, currentSize, MemoryRedisKeys.SUMMARY_THRESHOLD);
            return true;
        }
        
        if (isLongOffline(memoryIdStr) && hasEnoughNewMessages(memoryIdStr, currentSize)) {
            return true;
        }
        
        return false;
    }
    
    private boolean isLongOffline(String memoryIdStr) {
        Long lastActiveTime = userActivityTracker.getLastActiveTime(memoryIdStr);
        if (lastActiveTime == null) {
            return false;
        }
        return System.currentTimeMillis() - lastActiveTime > Duration.ofHours(2).toMillis();
    }
    
    private boolean hasEnoughNewMessages(String memoryIdStr, int currentSize) {
        Integer lastCompressedSize = getLastCompressedSize(memoryIdStr);
        if (lastCompressedSize == null) {
            log.debug("用户 {} 从未压缩过，触发压缩", memoryIdStr);
            return true;
        }
        int newMessages = currentSize - lastCompressedSize;
        if (newMessages >= MemoryRedisKeys.OFFLINE_NEW_MESSAGES_THRESHOLD) {
            if (log.isDebugEnabled()) {
                log.debug("用户 {} 离线 2h+，新消息 {} >= {}，触发压缩",
                        memoryIdStr, newMessages, MemoryRedisKeys.OFFLINE_NEW_MESSAGES_THRESHOLD);
            }
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("用户 {} 离线 2h+，但新消息 {} < {}，跳过压缩",
                    memoryIdStr, newMessages, MemoryRedisKeys.OFFLINE_NEW_MESSAGES_THRESHOLD);
        }
        return false;
    }
    
    /**
     * 获取上次压缩时的消息数
     */
    private Integer getLastCompressedSize(String memoryId) {
        String key = MemoryRedisKeys.LAST_COMPRESSED_SIZE_KEY + memoryId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("无效的压缩大小记录, memoryId={}, value={}", memoryId, value);
            return null;
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String historyKey = MemoryRedisKeys.HISTORY_KEY + memoryId;
        String summaryKey = MemoryRedisKeys.SUMMARY_KEY + memoryId;
        String lastCompressedSizeKey = MemoryRedisKeys.LAST_COMPRESSED_SIZE_KEY + memoryId;
        redisTemplate.delete(Arrays.asList(historyKey, summaryKey, lastCompressedSizeKey));
    }
}
