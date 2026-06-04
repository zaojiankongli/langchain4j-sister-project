package com.zjkl.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.emotion.model.Personality;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.settings.mapper.UserSettingsMapper;
import com.zjkl.settings.model.UserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户配置服务（MySQL 持久化 + Redis 缓存）
 *
 * 数据流向：
 *   read:  Caffeine → Redis → MySQL → 默认值
 *   write: MySQL → Redis → Caffeine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final String SETTINGS_KEY_PREFIX = "user:settings:";
    private static final String PERSONALITY_KEY_PREFIX = "user:personality:";
    private static final long REDIS_TTL_HOURS = 2;

    private final UserSettingsMapper settingsMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmotionService emotionService;

    /** L1 本地缓存（10 分钟过期） */
    private final Cache<String, UserSettings> settingsCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // ==================== 读 ====================

    /**
     * 获取用户配置：Caffeine → Redis → MySQL → 默认值
     */
    public UserSettings getSettings(String userId) {
        // 1. L1 本地缓存
        UserSettings cached = settingsCache.getIfPresent(userId);
        if (cached != null) return cached;

        // 2. L2 Redis 缓存
        UserSettings fromRedis = loadFromRedis(userId);
        if (fromRedis != null) {
            settingsCache.put(userId, fromRedis);
            return fromRedis;
        }

        // 3. MySQL 持久层
        UserSettings fromDb = settingsMapper.findByUserId(userId);
        if (fromDb != null) {
            saveToRedis(userId, fromDb);
            settingsCache.put(userId, fromDb);
            return fromDb;
        }

        // 4. 默认值
        UserSettings defaults = new UserSettings();
        settingsCache.put(userId, defaults);
        return defaults;
    }

    // ==================== 写 ====================

    /**
     * 保存用户配置（MySQL + Redis + Caffeine）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSettings(String userId, UserSettings settings) {
        // 1. MySQL 持久化
        settingsMapper.upsert(userId, settings);
        log.info("用户配置已写入 MySQL: userId={}", userId);

        // 2. Redis 缓存
        saveToRedis(userId, settings);

        // 3. 本地缓存
        settingsCache.put(userId, settings);
    }

    /**
     * 保存配置并同步人格设定到情绪引擎
     *
     * 注意：@Transactional 只覆盖 MySQL 操作。Redis 写入不在事务范围内，
     * 失败时通过 restoreNonTransactionalState() 进行补偿回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSettingsWithPersonality(String userId, UserSettings settings) {
        UserSettings previousSettings = settingsMapper.findByUserId(userId);
        try {
            saveSettings(userId, settings);

            // 保存人格预设/OCEAN 到 Redis（供情绪引擎读取）
            savePersonalityToRedis(userId, settings);

            applyRuntimeSettings(userId, settings);
        } catch (RuntimeException e) {
            restoreNonTransactionalState(userId, previousSettings);
            throw e;
        }
    }

    private void applyRuntimeSettings(String userId, UserSettings settings) {
        String preset = settings.getPersonalityPreset();
        if (preset != null && !preset.isEmpty()) {
            emotionService.setUserPersonality(userId, toPersonality(settings));
        }

        emotionService.setUserEmotionConfig(
                userId,
                settings.getSensitivity(),
                settings.getDecayRate(),
                settings.getRegressionRate()
        );
    }

    private void restoreNonTransactionalState(String userId, UserSettings previousSettings) {
        if (previousSettings == null) {
            redisTemplate.delete(SETTINGS_KEY_PREFIX + userId);
            redisTemplate.delete(PERSONALITY_KEY_PREFIX + userId);
            settingsCache.invalidate(userId);
            applyRuntimeSettings(userId, new UserSettings());
            return;
        }

        saveToRedis(userId, previousSettings);
        savePersonalityToRedis(userId, previousSettings);
        settingsCache.put(userId, previousSettings);
        applyRuntimeSettings(userId, previousSettings);
    }

    private Personality toPersonality(UserSettings settings) {
        if ("custom".equals(settings.getPersonalityPreset())) {
            return new Personality(
                    settings.getOpenness(),
                    settings.getConscientiousness(),
                    settings.getExtraversion(),
                    settings.getAgreeableness(),
                    settings.getNeuroticism()
            );
        }
        return Personality.fromPreset(settings.getPersonalityPreset());
    }

    // ==================== Redis 辅助 ====================

    private UserSettings loadFromRedis(String userId) {
        String key = SETTINGS_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, UserSettings.class);
        } catch (JsonProcessingException e) {
            log.warn("解析 Redis 用户配置失败: userId={}", userId, e);
            return null;
        }
    }

    private void saveToRedis(String userId, UserSettings settings) {
        String key = SETTINGS_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(settings);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(REDIS_TTL_HOURS));
        } catch (JsonProcessingException e) {
            log.error("序列化用户配置到 Redis 失败: userId={}", userId, e);
            throw new RuntimeException("缓存写入失败", e);
        }
    }

    private void savePersonalityToRedis(String userId, UserSettings settings) {
        String key = PERSONALITY_KEY_PREFIX + userId;
        if (!"custom".equals(settings.getPersonalityPreset())) {
            // 切换为非自定义预设时，清除旧的自定义 OCEAN 数据，避免残留干扰
            redisTemplate.delete(key);
            return;
        }

        try {
            var oceanMap = Map.of(
                    "openness", settings.getOpenness(),
                    "conscientiousness", settings.getConscientiousness(),
                    "extraversion", settings.getExtraversion(),
                    "agreeableness", settings.getAgreeableness(),
                    "neuroticism", settings.getNeuroticism()
            );
            String json = objectMapper.writeValueAsString(oceanMap);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(REDIS_TTL_HOURS));
            log.info("用户 OCEAN 人格特质已保存到 Redis: userId={}", userId);
        } catch (JsonProcessingException e) {
            log.error("序列化 OCEAN 人格特质失败: userId={}", userId, e);
            throw new RuntimeException("缓存写入失败", e);
        }
    }
}
