package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.emotion.config.EmotionEngineConfig;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.Personality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmotionServiceTest {

    @Mock
    private EmotionEngineConfig config;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps;

    private ObjectMapper objectMapper;
    private EmotionService emotionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(config.getDecayRate()).thenReturn(0.1);
        lenient().when(config.getRegressionRate()).thenReturn(0.05);
        lenient().when(config.getSensitivity()).thenReturn(0.5);
        lenient().when(config.getPersonalityType()).thenReturn("温柔害羞型");

        EmotionEngineConfig.PersonalityConfig pc = new EmotionEngineConfig.PersonalityConfig();
        pc.setOpenness(0.0);
        pc.setConscientiousness(0.0);
        pc.setExtraversion(-0.5);
        pc.setAgreeableness(0.6);
        pc.setNeuroticism(-0.2);
        lenient().when(config.getPersonality()).thenReturn(pc);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);

        emotionService = new EmotionService(config, redisTemplate, redissonClient, objectMapper);
    }

    @Test
    void newUserGetsDefaultEmotion() {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        EmotionalState state = emotionService.getUserEmotion("user-1");
        assertNotNull(state);
        // Default personality gentleAndShy() produces base PAD ≈ (0.099, -0.134, 0.022)
        assertEquals(0.099, state.getPleasure(), 1e-10);
        assertEquals(-0.134, state.getArousal(), 1e-10);
        assertEquals(0.022, state.getDominance(), 1e-10);
    }

    @Test
    void updateEmotionChangesPAD() throws InterruptedException {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // Get initial emotion
        EmotionalState initial = emotionService.getUserEmotion("user-1");
        double initP = initial.getPleasure();

        // Apply positive stimulus
        DeltaEmotion delta = DeltaEmotion.positive(1.0);
        EmotionalState updated = emotionService.updateUserEmotion("user-1", delta);

        // After applyDecayAndRegression: first sensitivity, then decay+regression
        // delta after sensitivity: ΔP = 0.3 * 0.5 = 0.15
        // P' = (initP + 0.15) * (1 - 0.1) + (bp - (initP + 0.15) * (1 - 0.1)) * 0.05
        // Simplified: after stimulus P should be higher than init
        double expectedAfterStimulus = initP + 0.3 * config.getSensitivity();
        assertTrue(updated.getPleasure() > initP,
                "Pleasure should increase after positive stimulus: " + updated.getPleasure() + " > " + initP);

        // The update should be within reasonable range
        assertTrue(updated.getPleasure() >= -1.0 && updated.getPleasure() <= 1.0);
        assertTrue(updated.getArousal() >= -1.0 && updated.getArousal() <= 1.0);
        assertTrue(updated.getDominance() >= -1.0 && updated.getDominance() <= 1.0);
    }

    @Test
    void decayConvergesToBaseEmotion() throws InterruptedException {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // Get base emotion
        EmotionalState base = emotionService.getUserEmotion("user-2");
        double bp = base.getPleasure();
        double ba = base.getArousal();
        double bd = base.getDominance();

        // Apply emotion change to push away from base
        emotionService.updateUserEmotion("user-2", DeltaEmotion.positive(1.0));

        // Decay multiple times - should converge back toward base
        EmotionalState prev = emotionService.getUserEmotion("user-2");
        for (int i = 0; i < 100; i++) {
            EmotionalState next = emotionService.decayUserEmotion("user-2");
            prev = next;
        }

        // After many decays, should be close to base
        EmotionalState finalState = emotionService.getUserEmotion("user-2");
        assertEquals(bp, finalState.getPleasure(), 0.01,
                "After many decays, pleasure should converge to base");
        assertEquals(ba, finalState.getArousal(), 0.01,
                "After many decays, arousal should converge to base");
        assertEquals(bd, finalState.getDominance(), 0.01,
                "After many decays, dominance should converge to base");
    }

    @Test
    void lockFailureReturnsCurrentEmotion() throws InterruptedException {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        EmotionalState before = emotionService.getUserEmotion("user-3");
        EmotionalState after = emotionService.updateUserEmotion("user-3", DeltaEmotion.positive(1.0));

        // When lock fails, the emotion should remain unchanged
        assertEquals(before.getPleasure(), after.getPleasure());
        assertEquals(before.getArousal(), after.getArousal());
        assertEquals(before.getDominance(), after.getDominance());
    }

    @Test
    void negativeStimulusDecreasesPleasure() throws InterruptedException {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        EmotionalState initial = emotionService.getUserEmotion("user-4");
        double initP = initial.getPleasure();

        emotionService.updateUserEmotion("user-4", DeltaEmotion.negative(1.0));
        EmotionalState after = emotionService.getUserEmotion("user-4");

        assertTrue(after.getPleasure() < initP,
                "Pleasure should decrease after negative stimulus");
    }

    @Test
    void resetReturnsToBaseEmotion() throws InterruptedException {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        EmotionalState base = emotionService.getUserEmotion("user-5");
        emotionService.updateUserEmotion("user-5", DeltaEmotion.positive(1.0));
        emotionService.resetUserEmotion("user-5");

        EmotionalState afterReset = emotionService.getUserEmotion("user-5");
        assertEquals(base.getPleasure(), afterReset.getPleasure());
        assertEquals(base.getArousal(), afterReset.getArousal());
        assertEquals(base.getDominance(), afterReset.getDominance());
    }

    @Test
    void moodLabelChangesWithState() {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Default state should give some label
        String label = emotionService.getUserMoodLabel("user-6");
        assertNotNull(label);
        assertFalse(label.isEmpty());
    }

    @Test
    void setPersonalityUpdatesCacheAndRedis() throws Exception {
        when(redisTemplate.opsForHash().entries(anyString())).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(redisTemplate.opsForValue().set(anyString(), anyString(), any())).thenReturn(true);

        Personality personality = Personality.gentleAndShy();
        emotionService.setUserPersonality("user-7", personality);

        verify(redisTemplate.opsForValue()).set(
                startsWith("user:personality:"),
                anyString(),
                any()
        );
    }
}
