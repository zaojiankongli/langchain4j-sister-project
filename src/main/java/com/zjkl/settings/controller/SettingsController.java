package com.zjkl.settings.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.emotion.model.Personality;
import com.zjkl.settings.model.UserSettings;
import com.zjkl.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户配置 API
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final UserContext userContext;

    /**
     * 获取用户全部配置
     */
    @GetMapping("/{userId}")
    public Result<UserSettings> getSettings(@PathVariable String userId) {
        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        UserSettings settings = settingsService.getSettings(userId);
        return Result.success(settings);
    }

    /**
     * 保存用户配置（同时更新人格、情绪引擎参数）
     */
    @PutMapping("/{userId}")
    public Result<Void> saveSettings(@PathVariable String userId, @Valid @RequestBody UserSettings settings) {
        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        
        // 保存配置并同步运行时人格/情绪参数
        settingsService.saveSettingsWithPersonality(userId, settings);

        return Result.success();
    }

    /**
     * 获取所有人格预设
     */
    @GetMapping("/presets")
    public Result<List<Map<String, Object>>> getPresets() {
        List<Map<String, Object>> presets = Personality.PRESETS.stream().map(name -> {
            Personality p = Personality.fromPreset(name);
            return Map.<String, Object>of(
                    "id", name,
                    "name", Personality.presetDisplayName(name),
                    "openness", p.getOpenness(),
                    "conscientiousness", p.getConscientiousness(),
                    "extraversion", p.getExtraversion(),
                    "agreeableness", p.getAgreeableness(),
                    "neuroticism", p.getNeuroticism()
            );
        }).toList();
        return Result.success(presets);
    }
}
