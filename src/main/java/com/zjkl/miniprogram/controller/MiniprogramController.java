package com.zjkl.miniprogram.controller;

import com.zjkl.ai.chat.realtime.DesktopOmniRealtimeSessionService;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeAudioChunk;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.miniprogram.dto.ChatSendRequest;
import com.zjkl.miniprogram.dto.DeviceBindRequest;
import com.zjkl.miniprogram.dto.MiniProgramProfileUpdateRequest;
import com.zjkl.miniprogram.service.MiniprogramService;
import com.zjkl.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/api/miniprogram")
@RequiredArgsConstructor
public class MiniprogramController {

    private final MiniprogramService miniprogramService;
    private final UserContext userContext;
    private final UserProfileService userProfileService;
    private final DesktopOmniRealtimeSessionService realtimeSessionService;

    @GetMapping("/home/summary")
    public Result<Map<String, Object>> homeSummary() {
        String userId = requireUserId();
        return Result.success(miniprogramService.getHomeSummary(userId));
    }

    @PostMapping("/device/bind")
    public Result<Map<String, Object>> bindDevice(@Valid @RequestBody DeviceBindRequest request) {
        String userId = requireUserId();
        return Result.success(miniprogramService.bindDevice(userId, request));
    }

    @GetMapping("/device/status")
    public Result<Map<String, Object>> deviceStatus() {
        String userId = requireUserId();
        return Result.success(miniprogramService.getDeviceStatus(userId));
    }

    @PostMapping("/chat/send")
    public Result<Map<String, Object>> sendChatMessage(@Valid @RequestBody ChatSendRequest request) {
        String userId = requireUserId();
        return Result.success(miniprogramService.sendChatMessage(userId, request));
    }

    @GetMapping("/chat/history")
    public Result<Map<String, Object>> chatHistory(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        String userId = requireUserId();
        return Result.success(miniprogramService.getChatHistory(userId, limit));
    }

    @GetMapping("/profile/sync")
    public Result<Map<String, Object>> syncProfile() {
        String userId = requireUserId();
        return Result.success(miniprogramService.syncProfile(userId));
    }

    @PostMapping("/profile/update")
    public Result<Map<String, Object>> updateProfile(@RequestBody MiniProgramProfileUpdateRequest request) {
        String userId = requireUserId();
        return Result.success(miniprogramService.updateProfile(userId, request));
    }

    @PostMapping("/upload/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) throws Exception {
        String userId = requireUserId();
        String url = userProfileService.uploadAvatar(userId, file);
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/background")
    public Result<Map<String, String>> uploadBackground(@RequestParam("file") MultipartFile file) throws Exception {
        String userId = requireUserId();
        String url = userProfileService.uploadBackground(userId, file);
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/voice")
    public Result<Map<String, Object>> uploadVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "transcript", required = false) String transcript) throws Exception {
        String userId = requireUserId();
        return Result.success(miniprogramService.uploadVoice(userId, file, text, transcript));
    }

    @PostMapping("/realtime/start")
    public Result<Map<String, String>> startRealtime(@RequestBody(required = false) PetRealtimeStartRequest request) {
        String userId = requireUserId();
        realtimeSessionService.start(userId, request != null ? request : new PetRealtimeStartRequest());
        return Result.success(Map.of("status", "started"));
    }

    @PostMapping("/realtime/audio")
    public Result<Map<String, String>> appendRealtimeAudio(@RequestBody PetRealtimeAudioChunk chunk) {
        String userId = requireUserId();
        if (chunk == null || chunk.getAudioBase64() == null || chunk.getAudioBase64().isBlank()) {
            return Result.error(400, "音频分片不能为空");
        }
        realtimeSessionService.appendAudio(userId, chunk.getAudioBase64());
        return Result.success(Map.of("status", "accepted"));
    }

    @PostMapping("/realtime/stop")
    public Result<Map<String, String>> stopRealtime() {
        String userId = requireUserId();
        realtimeSessionService.stop(userId);
        return Result.success(Map.of("status", "stopped"));
    }

    private String requireUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new com.zjkl.auth.exception.UnauthorizedException("请先登录");
        }
        return userId;
    }
}
