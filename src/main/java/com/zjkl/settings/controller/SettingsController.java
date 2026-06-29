package com.zjkl.settings.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.emotion.model.Personality;
import com.zjkl.settings.model.UserSettings;
import com.zjkl.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户配置 API。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/{userId}")
    public Result<UserSettings> getSettings(@PathVariable String userId) {
        return endpointMetrics.recordResult("web", "settings.get", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            UserSettings settings = settingsService.getSettings(userId);
            return Result.success(settings);
        });
    }

    @PutMapping("/{userId}")
    public Result<Void> saveSettings(@PathVariable String userId, @Valid @RequestBody UserSettings settings) {
        return endpointMetrics.recordResult("web", "settings.save", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            settingsService.saveSettingsWithPersonality(userId, settings);
            return Result.success();
        });
    }

    @GetMapping("/presets")
    public Result<List<Map<String, Object>>> getPresets() {
        return endpointMetrics.recordResult("web", "settings.presets", () -> {
            List<Map<String, Object>> presets = Personality.PRESETS.stream().map(name -> {
                Personality personality = Personality.fromPreset(name);
                return Map.<String, Object>of(
                        "id", name,
                        "name", Personality.presetDisplayName(name),
                        "openness", personality.getOpenness(),
                        "conscientiousness", personality.getConscientiousness(),
                        "extraversion", personality.getExtraversion(),
                        "agreeableness", personality.getAgreeableness(),
                        "neuroticism", personality.getNeuroticism()
                );
            }).toList();
            return Result.success(presets);
        });
    }
}
