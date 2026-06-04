package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.emotion.config.EmotionEngineConfig;
import com.zjkl.emotion.mapper.EmotionAnchorMapper;
import com.zjkl.emotion.mapper.UserEmotionMapper;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.Personality;
import com.zjkl.emotion.model.UserEmotionRecord;
import com.zjkl.emotion.model.vo.EmotionHistoryVO;
import com.zjkl.emotion.model.vo.EvolutionEventVO;
import com.zjkl.settings.mapper.UserSettingsMapper;
import com.zjkl.settings.model.UserSettings;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 情感计算引擎
 */
@Service
@Slf4j
public class EmotionService {

    private static final String EMOTION_KEY_PREFIX = "user:emotion:";
    private static final String PERSONALITY_KEY_PREFIX = "user:personality:";
    private static final String EMOTION_CONFIG_KEY_PREFIX = "user:emotion-config:";
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
    private final UserSettingsMapper userSettingsMapper;
    private final EmotionAnchorMapper emotionAnchorMapper;
    private final UserEmotionMapper userEmotionMapper;

    private final Cache<String, EmotionalState> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .expireAfterAccess(LOCAL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Personality> personalityCache = Caffeine.newBuilder()
            .maximumSize(PERSONALITY_CACHE_MAX_SIZE)
            .expireAfterAccess(PERSONALITY_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public EmotionService(EmotionEngineConfig config, StringRedisTemplate redisTemplate,
                          org.redisson.api.RedissonClient redissonClient, ObjectMapper objectMapper,
                          UserSettingsMapper userSettingsMapper,
                          EmotionAnchorMapper emotionAnchorMapper,
                          UserEmotionMapper userEmotionMapper) {
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.userSettingsMapper = userSettingsMapper;
        this.emotionAnchorMapper = emotionAnchorMapper;
        this.userEmotionMapper = userEmotionMapper;
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
        // 1. L1 本地缓存
        Personality cached = personalityCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        // 2. Redis 缓存
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

        // 3. MySQL 持久层回退
        Personality fromDb = loadPersonalityFromDb(userId);
        if (fromDb != null) {
            personalityCache.put(userId, fromDb);
            // 回写到 Redis，下次直接走缓存
            savePersonalityToRedis(userId, fromDb);
            return fromDb;
        }

        // 4. 默认值
        Personality defaultPersonality = Personality.gentleAndShy();
        personalityCache.put(userId, defaultPersonality);
        return defaultPersonality;
    }

    /**
     * 从 MySQL user_settings 表加载人格设定
     */
    private Personality loadPersonalityFromDb(String userId) {
        try {
            UserSettings settings = userSettingsMapper.findByUserId(userId);
            if (settings != null) {
                String preset = settings.getPersonalityPreset();
                if ("custom".equals(preset)) {
                    return new Personality(
                            settings.getOpenness(),
                            settings.getConscientiousness(),
                            settings.getExtraversion(),
                            settings.getAgreeableness(),
                            settings.getNeuroticism()
                    );
                }
                return Personality.fromPreset(preset);
            }
        } catch (Exception e) {
            log.warn("从 MySQL 加载人格设定失败: userId={}", userId, e);
        }
        return null;
    }

    /**
     * 回写人格到 Redis（key: user:personality:*）
     */
    private void savePersonalityToRedis(String userId, Personality personality) {
        try {
            String value = objectMapper.writeValueAsString(personality);
            redisTemplate.opsForValue().set(PERSONALITY_KEY_PREFIX + userId, value, PERSONALITY_TTL);
        } catch (Exception e) {
            log.warn("回写人格到 Redis 失败: userId={}", userId, e);
        }
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
        // 优先使用用户自定义的情绪引擎参数，如果没有则使用全局配置
        double decay = getUserDecayRate(userId);
        double regression = getUserRegressionRate(userId);
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
        // Null-safe extraction to prevent NPE from auto-unboxing
        double dp = delta.getDeltaP() != null ? delta.getDeltaP() : 0.0;
        double da = delta.getDeltaA() != null ? delta.getDeltaA() : 0.0;
        double dd = delta.getDeltaD() != null ? delta.getDeltaD() : 0.0;

        log.debug("情绪更新 - userId={}, 刺激：P={}, A={}, D={}", userId, dp, da, dd);

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
            double s = getUserSensitivity(userId);
            double newP = current.getPleasure() + dp * s;
            double newA = current.getArousal() + da * s;
            double newD = current.getDominance() + dd * s;
            current = new EmotionalState(newP, newA, newD);

            // 衰减只由 EmotionDecayScheduler 定时执行，此处不再调用 applyDecayAndRegression

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
        @SuppressWarnings({"rawtypes", "unchecked"})
        SessionCallback<Object> sessionCallback = new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                RedisOperations<String, String> ops = operations;
                ops.opsForHash().putAll(key, Map.of(
                        "pleasure", String.valueOf(emotion.getPleasure()),
                        "arousal", String.valueOf(emotion.getArousal()),
                        "dominance", String.valueOf(emotion.getDominance()),
                        "updatedAt", String.valueOf(System.currentTimeMillis())
                ));
                ops.expire(key, Duration.ofDays(EMOTION_EXPIRE_DAYS));
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

        // 删除 Redis 情感缓存，再刷新本地缓存，防止并发线程读到旧数据
        try {
            redisTemplate.delete(EMOTION_KEY_PREFIX + userId);
            log.debug("已清除用户情感缓存，将基于新人格重新计算: userId={}", userId);
        } catch (Exception e) {
            log.warn("清除用户情感缓存失败: userId={}", userId, e);
        }

        // Pre-populate local cache with base emotion from new personality to prevent stale reads
        EmotionalState basePAD = personality.toBasePAD();
        localCache.put(userId, basePAD);

        log.info("用户新的基础 PAD - P: {}, A: {}, D: {}",
                basePAD.getPleasure(), basePAD.getArousal(), basePAD.getDominance());
    }

    /**
     * 设置用户的情绪引擎参数（sensitivity / decayRate / regressionRate）
     * 这些参数将被EmotionDecayScheduler读取以实现按用户的情绪衰减
     */
    public void setUserEmotionConfig(String userId, double sensitivity, double decayRate, double regressionRate) {
        // 验证参数范围
        if (sensitivity < 0 || sensitivity > 1) {
            throw new IllegalArgumentException("sensitivity must be between 0 and 1");
        }
        if (decayRate < 0 || decayRate > 1) {
            throw new IllegalArgumentException("decayRate must be between 0 and 1");
        }
        if (regressionRate < 0 || regressionRate > 1) {
            throw new IllegalArgumentException("regressionRate must be between 0 and 1");
        }

        String key = EMOTION_CONFIG_KEY_PREFIX + userId;
        try {
            // 存储为JSON映射
            var configMap = Map.of(
                    "sensitivity", sensitivity,
                    "decayRate", decayRate,
                    "regressionRate", regressionRate
            );
            String json = objectMapper.writeValueAsString(configMap);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30)); // 与设置保持相同TTL
            log.info("用户情绪引擎配置已保存: userId={}, sensitivity={}, decayRate={}, regressionRate={}",
                    userId, sensitivity, decayRate, regressionRate);
        } catch (Exception e) {
            log.error("序列化用户情绪引擎配置失败: userId={}", userId, e);
            throw new RuntimeException("Failed to serialize emotion config", e);
        }
    }

    /**
     * 获取用户的敏感度参数，MySQL → Redis → 全局默认值
     */
    public double getUserSensitivity(String userId) {
        // 1. Redis
        double val = readEmotionConfigField(userId, "sensitivity");
        if (val >= 0) return val;
        // 2. MySQL 回退
        val = loadEmotionConfigFromDb(userId, "sensitivity");
        if (val >= 0) return val;
        // 3. 全局默认值
        return config.getSensitivity();
    }

    /**
     * 获取用户的衰减率参数，MySQL → Redis → 全局默认值
     */
    public double getUserDecayRate(String userId) {
        double val = readEmotionConfigField(userId, "decayRate");
        if (val >= 0) return val;
        val = loadEmotionConfigFromDb(userId, "decayRate");
        if (val >= 0) return val;
        return config.getDecayRate();
    }

    /**
     * 获取用户的回归率参数，MySQL → Redis → 全局默认值
     */
    public double getUserRegressionRate(String userId) {
        double val = readEmotionConfigField(userId, "regressionRate");
        if (val >= 0) return val;
        val = loadEmotionConfigFromDb(userId, "regressionRate");
        if (val >= 0) return val;
        return config.getRegressionRate();
    }

    // ==================== 情绪引擎参数辅助 ====================

    /**
     * 从 Redis user:emotion-config:* 解析指定字段，-1 表示无数据
     */
    @SuppressWarnings("unchecked")
    private double readEmotionConfigField(String userId, String field) {
        String key = EMOTION_CONFIG_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) return -1;
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object val = map.get(field);
            if (val != null) return Double.parseDouble(val.toString());
        } catch (Exception e) {
            log.warn("解析用户情绪引擎配置失败: userId={}, field={}", userId, field);
        }
        return -1;
    }

    /** 小缓存：MySQL 回退的情绪引擎参数，30s 过期防止频繁查库 */
    // 注意: 此 Caffeine 缓存未注册到 Spring CacheManager，不会出现在 actuator 指标中。
    // 如需统一管理，可迁移至 @Cacheable 或注册为 Spring Bean。
    private final Cache<String, Map<String, Double>> emotionConfigDbCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    /**
     * 从 MySQL user_settings 表回退读取情绪引擎参数，-1 表示无数据
     */
    private double loadEmotionConfigFromDb(String userId, String field) {
        Map<String, Double> cached = emotionConfigDbCache.getIfPresent(userId);
        if (cached == null) {
            try {
                UserSettings settings = userSettingsMapper.findByUserId(userId);
                if (settings == null) return -1;
                cached = Map.of(
                        "sensitivity", settings.getSensitivity(),
                        "decayRate", settings.getDecayRate(),
                        "regressionRate", settings.getRegressionRate()
                );
                emotionConfigDbCache.put(userId, cached);
            } catch (Exception e) {
                log.warn("从 MySQL 加载情绪引擎参数失败: userId={}", userId, e);
                return -1;
            }
        }
        Double val = cached.get(field);
        return val != null ? val : -1;
    }

    // ==================== 情绪历史与演变 ====================

    /**
     * 获取用户性格演变事件列表
     */
    @Transactional(readOnly = true)
    public List<EvolutionEventVO> getEvolutionEvents(String userId, int limit) {
        List<Map<String, Object>> events = emotionAnchorMapper.selectEvolutionByUserId(userId, limit);
        return events.stream().map(row -> {
            EvolutionEventVO vo = new EvolutionEventVO();
            vo.setTrigger((String) row.get("trigger_reason"));
            String highlightTraits = (String) row.get("highlight_traits");
            vo.setResult(highlightTraits != null && !highlightTraits.isEmpty()
                    ? highlightTraits
                    : (String) row.get("ai_reflection"));
            Object startTimeObj = row.get("start_time");
            LocalDateTime startTime = null;
            if (startTimeObj instanceof LocalDateTime) {
                startTime = (LocalDateTime) startTimeObj;
            } else if (startTimeObj instanceof Date) {
                startTime = ((Date) startTimeObj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            vo.setTime(formatRelativeTime(startTime));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取用户情绪历史记录
     */
    @Transactional(readOnly = true)
    public List<EmotionHistoryVO> getEmotionHistory(String userId) {
        return getEmotionHistory(userId, 200);
    }

    @Transactional(readOnly = true)
    public List<EmotionHistoryVO> getEmotionHistory(String userId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 500));
        List<UserEmotionRecord> records = userEmotionMapper.selectByUserId(userId, 0, cappedLimit);
        return records.stream().map(r -> {
            EmotionHistoryVO vo = new EmotionHistoryVO();
            vo.setId(r.getId());
            vo.setPleasure(r.getPleasure());
            vo.setArousal(r.getArousal());
            vo.setDominance(r.getDominance());
            vo.setMoodDescription(r.getMoodDescription());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 格式化相对时间
     */
    private static String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = duration.toHours();
        if (hours < 24) return hours + "小时前";
        long days = duration.toDays();
        if (days < 30) return days + "天前";
        return dateTime.toLocalDate().toString().replace("-", ".");
    }
}
