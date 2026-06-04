package com.zjkl.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.emotion.model.Personality;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.settings.mapper.UserSettingsMapper;
import com.zjkl.settings.model.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsMapper settingsMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EmotionService emotionService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        settingsService = new SettingsService(settingsMapper, redisTemplate, objectMapper, emotionService);
    }

    @Test
    void saveSettingsWithPersonality_shouldRestorePreviousRuntimeStateWhenEmotionConfigSyncFails() throws Exception {
        UserSettings previous = new UserSettings();
        previous.setPersonalityPreset("gentleAndShy");
        previous.setSensitivity(0.5);
        previous.setDecayRate(0.1);
        previous.setRegressionRate(0.05);

        UserSettings updated = new UserSettings();
        updated.setPersonalityPreset("lively");
        updated.setSensitivity(0.8);
        updated.setDecayRate(0.2);
        updated.setRegressionRate(0.15);

        when(settingsMapper.findByUserId("u1")).thenReturn(previous);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        org.mockito.Mockito.doThrow(new RuntimeException("sync failed"))
                .when(emotionService)
                .setUserEmotionConfig("u1", 0.8, 0.2, 0.15);

        assertThrows(RuntimeException.class, () -> settingsService.saveSettingsWithPersonality("u1", updated));

        verify(emotionService).setUserPersonality("u1", Personality.fromPreset("lively"));
        verify(emotionService).setUserEmotionConfig("u1", 0.8, 0.2, 0.15);
        verify(emotionService).setUserPersonality("u1", Personality.fromPreset("gentleAndShy"));
        verify(emotionService).setUserEmotionConfig("u1", 0.5, 0.1, 0.05);
        verify(settingsMapper).upsert(eq("u1"), eq(updated));
    }

    @Test
    void saveSettingsWithPersonality_shouldRestoreDefaultRuntimeStateWhenNoPreviousSettingsExist() throws Exception {
        UserSettings updated = new UserSettings();
        updated.setPersonalityPreset("lively");
        updated.setSensitivity(0.8);
        updated.setDecayRate(0.2);
        updated.setRegressionRate(0.15);

        when(settingsMapper.findByUserId("u2")).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        org.mockito.Mockito.doThrow(new RuntimeException("sync failed"))
                .when(emotionService)
                .setUserEmotionConfig("u2", 0.8, 0.2, 0.15);

        assertThrows(RuntimeException.class, () -> settingsService.saveSettingsWithPersonality("u2", updated));

        verify(emotionService).setUserPersonality("u2", Personality.fromPreset("lively"));
        verify(emotionService).setUserEmotionConfig("u2", 0.8, 0.2, 0.15);
        verify(redisTemplate).delete("user:settings:u2");
        verify(redisTemplate, atLeastOnce()).delete("user:personality:u2");
        verify(emotionService).setUserPersonality("u2", Personality.gentleAndShy());
        verify(emotionService).setUserEmotionConfig("u2", 0.5, 0.1, 0.05);
        verify(settingsMapper).upsert(eq("u2"), eq(updated));
        verify(settingsMapper, never()).upsert(eq("u2"), eq(new UserSettings()));
    }
}
