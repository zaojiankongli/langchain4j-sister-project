package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.emotion.config.EmotionEngineConfig;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.Personality;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 情感计算引擎
 */
@Service
@Slf4j
public class EmotionService {

    private static final String EMOTION_KEY_PREFIX = "user:emotion:";
    private static final String PERSONALITY_KEY_PREFIX = "user:personality:";
    private static final Long EMOTION_EXPIRE_DAYS = 7L;
    private static final long LOCK_WAIT_SECONDS = 1;
    private static final long LOCK_LEASE_SECONDS = 5;
    private static final Duration PERSONALITY_TTL = Duration.ofDays(30);
    private static final int LOCAL_CACHE_MAX_SIZE = 10000;
    private static final int LOCAL_CACHE_EXPIRE_MINUTES = 30;
    private static final int PERSONALITY_CACHE_MAX_SIZE = 5000;
    private static final int PERSONALITY_CACHE_EXPIRE_MINUTES = 10;

    private final EmotionEngineConfig config;
    private final StringRedisTemplate redisTemplate;
    private final org.redisson.api.RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, EmotionalState> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .expireAfterAccess(LOCAL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Personality> personalityCache = Caffeine.newBuilder()
            .maximumSize(PERSONALITY_CACHE_MAX_SIZE)
            .expireAfterAccess(PERSONALITY_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public EmotionService(EmotionEngineConfig config, StringRedisTemplate redisTemplate,
                          org.redisson.api.RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("初始化情感计算引擎 - 默认人设：{}", config.getPersonalityType());
        EmotionEngineConfig.PersonalityConfig pc = config.getPersonality();
        log.info("默认 OCEAN 人格 - O: {}, C: {}, E: {}, A: {}, N: {}",
                pc.getOpenness(), pc.getConscientiousness(),
                pc.getExtraversion(), pc.getAgreeableness(), pc.getNeuroticism());
    }

    private EmotionalState computeBaseEmotion(String userId) {
        Personality personality = getUserPersonality(userId);
        return personality.toBasePAD();
    }

    private Personality getUserPersonality(String userId) {
        Personality cached = personalityCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        String key = PERSONALITY_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            try {
                Personality p = objectMapper.readValue(json, Personality.class);
                personalityCache.put(userId, p);
                return p;
            } catch (Exception e) {
                log.warn("解析用户个性配置失败: userId={}, value={}", userId, json);
            }
        }
        Personality defaultPersonality = Personality.gentleAndShy();
        personalityCache.put(userId, defaultPersonality);
        return defaultPersonality;
    }



    /**
     * 获取用户情绪状态（缓存 → Redis）
     */
    public EmotionalState getUserEmotion(String userId) {
        EmotionalState cached = localCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        String key = EMOTION_KEY_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        EmotionalState emotion;
        if (entries.isEmpty()) {
            emotion = computeBaseEmotion(userId);
            log.debug("新用户，走默认值：userId={}", userId);
        } else {
            emotion = new EmotionalState(
                    parseDouble(entries.get("pleasure")),
                    parseDouble(entries.get("arousal")),
                    parseDouble(entries.get("dominance"))
            );
        }

        localCache.put(userId, emotion);
        return emotion;
    }

    private EmotionalState applyDecayAndRegression(EmotionalState state, String userId) {
        double decay = config.getDecayRate();
        double regression = config.getRegressionRate();
        EmotionalState base = computeBaseEmotion(userId);
        double bp = base.getPleasure();
        double ba = base.getArousal();
        double bd = base.getDominance();

        double p = state.getPleasure();
        double a = state.getArousal();
        double d = state.getDominance();

        // 先衰减（按比例消散），再回归（向基线靠拢）
        p = p * (1 - decay);
        p = p + (bp - p) * regression;
        a = a * (1 - decay);
        a = a + (ba - a) * regression;
        d = d * (1 - decay);
        d = d + (bd - d) * regression;

        return new EmotionalState(p, a, d);
    }

    /**
     * 更新用户情绪状态
     */
    public EmotionalState updateUserEmotion(String userId, DeltaEmotion delta) {
        log.debug("情绪更新 - userId={}, 刺激：P={}, A={}, D={}",
                userId, delta.getDeltaP(), delta.getDeltaA(), delta.getDeltaD());

        org.redisson.api.RLock lock = redissonClient.getLock("lock:emotion:" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取情绪锁失败，跳过本次更新 - userId={}", userId);
                return getUserEmotion(userId);
            }

            EmotionalState current = getUserEmotion(userId);

            // 施加刺激（基于当前情绪的增量变化）
            double s = config.getSensitivity();
            double newP = current.getPleasure() + delta.getDeltaP() * s;
            double newA = current.getArousal() + delta.getDeltaA() * s;
            double newD = current.getDominance() + delta.getDeltaD() * s;
            current = new EmotionalState(newP, newA, newD);

            // 衰减 + 回归基准
            current = applyDecayAndRegression(current, userId);

            saveUserEmotion(userId, current);

            log.debug("用户情绪更新完成 - userId={}, P: {}, A: {}, D: {}",
                    userId, current.getPleasure(), current.getArousal(), current.getDominance());

            return current.copy();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取情绪锁被中断 - userId={}", userId);
            return getUserEmotion(userId);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 情绪自然衰减
     */
    public EmotionalState decayUserEmotion(String userId) {
        log.debug("用户情绪自然衰减：userId={}", userId);

        org.redisson.api.RLock lock = redissonClient.getLock("lock:emotion:" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取情绪锁失败，跳过衰减 - userId={}", userId);
                return getUserEmotion(userId);
            }

            EmotionalState current = getUserEmotion(userId);
            current = applyDecayAndRegression(current, userId);

            saveUserEmotion(userId, current);

            return current.copy();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取情绪锁被中断 - userId={}", userId);
            return getUserEmotion(userId);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public String getUserMoodDescription(String userId) {
        return MoodDescriptionGenerator.generateMoodDescription(getUserEmotion(userId));
    }

    public String getUserMoodLabel(String userId) {
        return MoodDescriptionGenerator.generateMoodLabel(getUserEmotion(userId));
    }

    public String getMoodDescription(EmotionalState emotion) {
        return MoodDescriptionGenerator.generateMoodDescription(emotion);
    }

    private void saveUserEmotion(String userId, EmotionalState emotion) {
        String key = EMOTION_KEY_PREFIX + userId;
        SessionCallback<Object> sessionCallback = new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForHash().putAll(key, Map.of(
                        "pleasure", String.valueOf(emotion.getPleasure()),
                        "arousal", String.valueOf(emotion.getArousal()),
                        "dominance", String.valueOf(emotion.getDominance()),
                        "updatedAt", String.valueOf(System.currentTimeMillis())
                ));
                operations.expire(key, Duration.ofDays(EMOTION_EXPIRE_DAYS));
                return null;
            }
        };
        redisTemplate.execute(sessionCallback);

        localCache.put(userId, emotion);
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }





    /**
     * 获取用户情感基数（基于她的个性）
     */
    public EmotionalState getBaseEmotion(String userId) {
        return computeBaseEmotion(userId).copy();
    }

    public Personality getPersonality(String userId) {
        return getUserPersonality(userId);
    }

    public void resetUserEmotion(String userId) {
        log.info("重置到基准：userId={}", userId);
        saveUserEmotion(userId, computeBaseEmotion(userId).copy());
    }

    /**
     * 设置用户的个性（每个用户各自独立）
     */
    public void setUserPersonality(String userId, Personality personality) {
        if (personality == null) {
            throw new IllegalArgumentException("personality must not be null");
        }
        log.info("设置用户个性化人格 - userId={}, O: {}, C: {}, E: {}, A: {}, N: {}",
                userId,
                personality.getOpenness(), personality.getConscientiousness(),
                personality.getExtraversion(), personality.getAgreeableness(),
                personality.getNeuroticism());

        String key = PERSONALITY_KEY_PREFIX + userId;
        try {
            String value = objectMapper.writeValueAsString(personality);
            redisTemplate.opsForValue().set(key, value, PERSONALITY_TTL);
        } catch (Exception e) {
            log.error("序列化用户个性配置失败: userId={}", userId, e);
            throw new RuntimeException("Failed to serialize personality", e);
        }

        personalityCache.put(userId, personality);
        localCache.invalidate(userId);

        EmotionalState basePAD = personality.toBasePAD();
        log.info("用户新的基础 PAD - P: {}, A: {}, D: {}",
                basePAD.getPleasure(), basePAD.getArousal(), basePAD.getDominance());
    }
}
