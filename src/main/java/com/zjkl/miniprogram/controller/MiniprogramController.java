package com.zjkl.miniprogram.controller;

import com.zjkl.ai.chat.realtime.DesktopOmniRealtimeSessionService;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeAudioChunk;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/miniprogram")
@RequiredArgsConstructor
public class MiniprogramController {

    private final MiniprogramService miniprogramService;
    private final UserContext userContext;
    private final UserProfileService userProfileService;
    private final DesktopOmniRealtimeSessionService realtimeSessionService;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/home/summary")
    public Result<Map<String, Object>> homeSummary() {
        return endpointMetrics.recordResult("miniprogram", "home.summary", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.getHomeSummary(userId));
        });
    }

    @PostMapping("/device/bind")
    public Result<Map<String, Object>> bindDevice(@Valid @RequestBody DeviceBindRequest request) {
        return endpointMetrics.recordResult("miniprogram", "device.bind", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.bindDevice(userId, request));
        });
    }

    @GetMapping("/device/status")
    public Result<Map<String, Object>> deviceStatus() {
        return endpointMetrics.recordResult("miniprogram", "device.status", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.getDeviceStatus(userId));
        });
    }

    @PostMapping("/chat/send")
    public Result<Map<String, Object>> sendChatMessage(@Valid @RequestBody ChatSendRequest request) {
        return endpointMetrics.recordResult("miniprogram", "chat.send", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.sendChatMessage(userId, request));
        });
    }

    @GetMapping("/chat/history")
    public Result<Map<String, Object>> chatHistory(@RequestParam(defaultValue = "20") int limit) {
        return endpointMetrics.recordResult("miniprogram", "chat.history", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.getChatHistory(userId, limit));
        });
    }

    @GetMapping("/profile/sync")
    public Result<Map<String, Object>> syncProfile() {
        return endpointMetrics.recordResult("miniprogram", "profile.sync", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.syncProfile(userId));
        });
    }

    @PostMapping("/profile/update")
    public Result<Map<String, Object>> updateProfile(@RequestBody MiniProgramProfileUpdateRequest request) {
        return endpointMetrics.recordResult("miniprogram", "profile.update", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.updateProfile(userId, request));
        });
    }

    @PostMapping("/upload/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) throws Exception {
        return endpointMetrics.recordCheckedResult("miniprogram", "upload.avatar", () -> {
            String userId = requireUserId();
            String url = userProfileService.uploadAvatar(userId, file);
            return Result.success(Map.of("url", url));
        });
    }

    @PostMapping("/upload/background")
    public Result<Map<String, String>> uploadBackground(@RequestParam("file") MultipartFile file) throws Exception {
        return endpointMetrics.recordCheckedResult("miniprogram", "upload.background", () -> {
            String userId = requireUserId();
            String url = userProfileService.uploadBackground(userId, file);
            return Result.success(Map.of("url", url));
        });
    }

    @PostMapping("/upload/voice")
    public Result<Map<String, Object>> uploadVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "transcript", required = false) String transcript) throws Exception {
        return endpointMetrics.recordCheckedResult("miniprogram", "upload.voice", () -> {
            String userId = requireUserId();
            return Result.success(miniprogramService.uploadVoice(userId, file, text, transcript));
        });
    }

    @PostMapping("/realtime/start")
    public Result<Map<String, String>> startRealtime(@RequestBody(required = false) PetRealtimeStartRequest request) {
        return endpointMetrics.recordResult("miniprogram", "realtime.start", () -> {
            String userId = requireUserId();
            realtimeSessionService.start(userId, request != null ? request : new PetRealtimeStartRequest());
            return Result.success(Map.of("status", "started"));
        });
    }

    @PostMapping("/realtime/audio")
    public Result<Map<String, String>> appendRealtimeAudio(@RequestBody PetRealtimeAudioChunk chunk) {
        return endpointMetrics.recordResult("miniprogram", "realtime.audio", () -> {
            String userId = requireUserId();
            if (chunk == null || chunk.getAudioBase64() == null || chunk.getAudioBase64().isBlank()) {
                return Result.error(400, "音频分片不能为空");
            }
            realtimeSessionService.appendAudio(userId, chunk.getAudioBase64());
            return Result.success(Map.of("status", "accepted"));
        });
    }

    @PostMapping("/realtime/stop")
    public Result<Map<String, String>> stopRealtime() {
        return endpointMetrics.recordResult("miniprogram", "realtime.stop", () -> {
            String userId = requireUserId();
            realtimeSessionService.stop(userId);
            return Result.success(Map.of("status", "stopped"));
        });
    }

    private String requireUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }
}
