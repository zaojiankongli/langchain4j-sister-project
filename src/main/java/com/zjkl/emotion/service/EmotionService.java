package com.zjkl.emotion.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.emotion.config.EmotionEngineConfig;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.Personality;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

    private final Cache<String, EmotionalState> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .expireAfterAccess(LOCAL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Personality> personalityCache = Caffeine.newBuilder()
            .maximumSize(PERSONALITY_CACHE_MAX_SIZE)
            .expireAfterAccess(PERSONALITY_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public EmotionService(EmotionEngineConfig config, StringRedisTemplate redisTemplate,
                          org.redisson.api.RedissonClient redissonClient) {
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
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
                String[] parts = json.split(",");
                if (parts.length == 5) {
                    Personality p = new Personality(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4])
                    );
                    personalityCache.put(userId, p);
                    return p;
                }
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

        p = p * (1 - decay) + (bp - p) * regression;
        a = a * (1 - decay) + (ba - a) * regression;
        d = d * (1 - decay) + (bd - d) * regression;

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
        EmotionalState emotion = getUserEmotion(userId);
        return getMoodDescription(emotion);
    }

    public String getMoodDescription(EmotionalState emotion) {
        return generateMoodDescription(emotion.getPleasure(), emotion.getArousal(), emotion.getDominance());
    }

    public String getUserMoodLabel(String userId) {
        EmotionalState emotion = getUserEmotion(userId);
        return generateMoodLabel(emotion.getPleasure(), emotion.getArousal(), emotion.getDominance());
    }

    private void saveUserEmotion(String userId, EmotionalState emotion) {
        String key = EMOTION_KEY_PREFIX + userId;
        redisTemplate.opsForHash().putAll(key, Map.of(
                "pleasure", String.valueOf(emotion.getPleasure()),
                "arousal", String.valueOf(emotion.getArousal()),
                "dominance", String.valueOf(emotion.getDominance()),
                "updatedAt", String.valueOf(System.currentTimeMillis())
        ));
        redisTemplate.expire(key, Duration.ofDays(EMOTION_EXPIRE_DAYS));

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

    private String generateMoodDescription(double p, double a, double d) {
        StringBuilder mood = new StringBuilder();

        // 害羞
        if (d < -0.5) {
            mood.append("羞涩得不敢抬头，脸颊发烫，手指紧张地绞着衣角，声音轻得像蚊子哼哼");
        } else if (d < -0.3) {
            mood.append("有些害羞，微微低着头，偶尔偷看你一眼又迅速移开视线");
        }
        // 开心
        else if (p > 0.5) {
            mood.append("心里甜甜的，眼睛亮晶晶的，嘴角忍不住微微上扬");
        } else if (p > 0.2) {
            mood.append("心情不错，嘴角带着淡淡的笑意，眼神很温柔");
        }
        // 难过
        else if (p < -0.4) {
            mood.append("心里酸酸的，眼眶有些发热，声音也变得哽咽");
        } else if (p < -0.15) {
            mood.append("心情有些低落，低着头不说话，手指无意识地摆弄着衣角");
        }
        // 紧张
        else if (a > 0.5) {
            mood.append("心跳得好快，手心都在出汗，说话都有些结巴了");
        } else if (a > 0.2) {
            mood.append("有点紧张，手指轻轻按在胸口，试图让心跳平复下来");
        }
        // 平静
        else if (a < -0.5) {
            mood.append("整个人很放松，像躺在云朵上一样，说话声音轻柔得像呢喃");
        } else if (a < -0.2) {
            mood.append("感觉很安心，身体放松地靠在那里，眼神柔和");
        }
        else {
            mood.append("安静地待在那里，眼神温和，带着淡淡的微笑");
        }

        if (p > 0.3 && a > 0.3 && d < -0.2) {
            mood.append("...心里像有小鹿乱撞，既开心又害羞，捂住发烫的脸从指缝里偷看你");
        }
        else if (d < -0.4 && p > 0.1) {
            mood.append("...乖巧地听你说话，眼神里满是信任和依赖");
        }
        else if (p < -0.2 && d < -0.2) {
            mood.append("...咬着嘴唇不说话，努力不让眼泪掉下来");
        }

        return mood.toString();
    }

    private String generateMoodLabel(double p, double a, double d) {
        if (d < -0.5) return "极度害羞";
        if (d < -0.3) return "有些害羞";
        if (p > 0.5) return "非常开心";
        if (p > 0.2) return "心情不错";
        if (p < -0.4) return "很难过";
        if (p < -0.15) return "有点失落";
        if (a > 0.5) return "非常紧张";
        if (a > 0.2) return "有些紧张";
        if (a < -0.5) return "非常平静";
        if (a < -0.2) return "比较放松";
        return "平静";
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
        String value = String.join(",",
                String.valueOf(personality.getOpenness()),
                String.valueOf(personality.getConscientiousness()),
                String.valueOf(personality.getExtraversion()),
                String.valueOf(personality.getAgreeableness()),
                String.valueOf(personality.getNeuroticism()));
        redisTemplate.opsForValue().set(key, value, PERSONALITY_TTL);

        personalityCache.put(userId, personality);
        localCache.invalidate(userId);

        EmotionalState basePAD = personality.toBasePAD();
        log.info("用户新的基础 PAD - P: {}, A: {}, D: {}",
                basePAD.getPleasure(), basePAD.getArousal(), basePAD.getDominance());
    }
}
