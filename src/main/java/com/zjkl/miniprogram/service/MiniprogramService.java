package com.zjkl.miniprogram.service;

import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.mapper.ConverMessageMapper;
import com.zjkl.ai.chat.service.SisterChatService;
import com.zjkl.ai.oss.service.OssService;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.emotion.model.DeltaEmotion;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.ChatReplyPersistenceService;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.emotion.service.MoodDescriptionGenerator;
import com.zjkl.emotion.util.LlmResponseStreamParser;
import com.zjkl.miniprogram.domain.UserDevice;
import com.zjkl.miniprogram.dto.ChatSendRequest;
import com.zjkl.miniprogram.dto.DeviceBindRequest;
import com.zjkl.miniprogram.dto.MiniProgramProfileUpdateRequest;
import com.zjkl.miniprogram.mapper.UserDeviceMapper;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class MiniprogramService {

    private final UserDeviceMapper userDeviceMapper;
    private final UserMapper userMapper;
    private final ConverMessageMapper converMessageMapper;
    private final OssService ossService;
    private final SisterChatService sisterChatService;
    private final LlmResponseStreamParser llmResponseStreamParser;
    private final ChatReplyPersistenceService chatReplyPersistenceService;
    private final EmotionService emotionService;

    public Map<String, Object> getHomeSummary(String userId) {
        User user = userMapper.findById(userId);
        UserDevice device = userDeviceMapper.findByUserId(userId);
        String lastReply = latestAssistantReply(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("petName", device != null ? safeDeviceName(device) : "未绑定桌宠");
        result.put("deviceName", device != null ? safeDeviceName(device) : "--");
        result.put("connectionStatus", device != null ? safeStatus(device.getStatus()) : "未绑定");
        result.put("mood", "平静");
        result.put("unreadCount", 0);
        result.put("lastReply", lastReply != null ? lastReply : "先完成设备绑定，然后就可以开始聊天。");
        result.put("syncAt", LocalDateTime.now());
        if (user != null) {
            result.put("username", user.getUsername());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindDevice(String userId, DeviceBindRequest request) {
        String deviceCode = request.deviceCode().trim();
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? deviceCode
                : request.nickname().trim();

        UserDevice existingByCode = userDeviceMapper.findByDeviceCode(deviceCode);
        if (existingByCode != null && !userId.equals(existingByCode.getUserId())) {
            throw new BusinessException(409, "该设备码已绑定其他账号");
        }

        UserDevice existing = userDeviceMapper.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        UserDevice device = UserDevice.builder()
                .userId(userId)
                .deviceCode(deviceCode)
                .nickname(nickname)
                .status("离线")
                .boundAt(now)
                .lastSeenAt(null)
                .build();

        if (existing == null) {
            userDeviceMapper.insert(device);
        } else {
            userDeviceMapper.updateForUser(device);
        }

        return toDeviceStatus(device);
    }

    public Map<String, Object> getDeviceStatus(String userId) {
        UserDevice device = userDeviceMapper.findByUserId(userId);
        if (device == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("deviceCode", null);
            result.put("nickname", null);
            result.put("status", "未绑定");
            return result;
        }
        return toDeviceStatus(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sendChatMessage(String userId, ChatSendRequest request) {
        String content = request.content().trim();
        return generateChatReply(userId, content, request.imageUrl());
    }

    public Map<String, Object> getChatHistory(String userId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        List<ConverMessage> latest = converMessageMapper.selectLatestByUserId(userId, cappedLimit);
        List<Map<String, Object>> list = latest == null
                ? List.of()
                : latest.reversed().stream().map(this::toMiniMessage).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        return result;
    }

    public Map<String, Object> uploadVoice(String userId, MultipartFile file, String text, String transcript) throws IOException {
        String url = ossService.uploadVoice(userId, file);
        String recognizedText = firstNonBlank(transcript, text);

        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("text", recognizedText);
        result.put("asrStatus", recognizedText == null ? "PENDING_ASR" : "PROVIDED");

        if (recognizedText == null) {
            result.put("message", "语音已上传，后端文件 ASR 服务尚未接入；可传入 transcript/text 直接进入聊天链路");
            return result;
        }

        result.putAll(generateChatReply(userId, recognizedText, null));
        return result;
    }

    private Map<String, Object> generateChatReply(String userId, String content, String imageUrl) {
        try {
            SisterChatService.ChatResult chatResult = sisterChatService.chatWithVoice(content, userId, imageUrl);
            LlmResponseStreamParser.ParsedResult parsed = llmResponseStreamParser.parse(chatResult.stream());
            String reply = awaitReply(parsed);
            if (reply.isBlank()) {
                throw new BusinessException(500, "AI 回复为空，请稍后重试");
            }

            CompletableFuture<String> imageDescFuture = chatResult.imageDescFuture();
            chatReplyPersistenceService.saveChatMemory(userId, content, imageUrl, imageDescFuture, reply);
            updateEmotion(userId, parsed);

            Map<String, Object> result = new HashMap<>();
            result.put("reply", reply);
            result.put("messages", latestMessagesForResponse(userId));
            result.put("mood", emotionService.getUserMoodLabel(userId));
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "AI 回复生成失败，请稍后重试");
        }
    }

    private String awaitReply(LlmResponseStreamParser.ParsedResult parsed) throws TimeoutException {
        try {
            List<String> chunks = parsed.getReplyStream()
                    .collectList()
                    .timeout(java.time.Duration.ofSeconds(45))
                    .block();
            return chunks == null ? "" : String.join("", chunks).trim();
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof TimeoutException timeoutException) {
                throw timeoutException;
            }
            throw e;
        }
    }

    private void updateEmotion(String userId, LlmResponseStreamParser.ParsedResult parsed) {
        try {
            DeltaEmotion delta = parsed.getDeltaEmotion()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .onErrorReturn(new DeltaEmotion(0.0, 0.0, 0.0))
                    .block();
            if (delta == null) {
                return;
            }
            EmotionalState newEmotion = emotionService.updateUserEmotion(userId, delta);
            String moodLabel = MoodDescriptionGenerator.generateMoodLabel(newEmotion);
            if (moodLabel != null) {
                // Accessing the label ensures parity with desktop emotion update flow without pushing WebSocket events here.
            }
        } catch (Exception ignored) {
            // 情绪更新失败不应阻塞小程序聊天主链路。
        }
    }

    private List<Map<String, Object>> latestMessagesForResponse(String userId) {
        List<ConverMessage> latest = converMessageMapper.selectLatestByUserId(userId, 2);
        if (latest == null) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>(latest.reversed().stream().map(this::toMiniMessage).toList());
        return messages;
    }

    public Map<String, Object> syncProfile(String userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nickname", displayName(user));
        result.put("username", user.getUsername());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("backgroundUrl", user.getBackgroundUrl());
        result.put("mood", "平静");
        result.put("accountStatus", "已登录");
        result.put("gender", user.getGender());
        result.put("hobbies", user.getHobbies());
        result.put("aiType", user.getAiType());
        result.put("birthday", user.getBirthday());
        result.put("email", user.getEmail());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProfile(String userId, MiniProgramProfileUpdateRequest request) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        String nickname = firstNonBlank(request.nickname(), request.username());
        if (nickname != null) {
            user.setUsername(nickname);
        }
        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }
        if (request.backgroundUrl() != null && !request.backgroundUrl().isBlank()) {
            user.setBackgroundUrl(request.backgroundUrl().trim());
        }

        userMapper.update(user);
        return syncProfile(userId);
    }

    private Map<String, Object> toDeviceStatus(UserDevice device) {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceCode", device.getDeviceCode());
        result.put("nickname", device.getNickname());
        result.put("status", safeStatus(device.getStatus()));
        result.put("boundAt", device.getBoundAt());
        result.put("lastSeenAt", device.getLastSeenAt());
        return result;
    }

    private String latestAssistantReply(String userId) {
        List<ConverMessage> messages = converMessageMapper.selectLatestByUserId(userId, 20);
        if (messages == null) {
            return null;
        }
        for (ConverMessage message : messages) {
            if (!"assistant".equals(message.getRole()) || message.getContents() == null) {
                continue;
            }
            for (MessageContent content : message.getContents()) {
                if ("text".equals(content.getType()) && content.getText() != null && !content.getText().isBlank()) {
                    return content.getText();
                }
            }
        }
        return null;
    }

    private Map<String, Object> toMiniMessage(ConverMessage message) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("role", message.getRole());
        result.put("type", "text");
        result.put("content", firstText(message));
        result.put("time", message.getCreatedAt());
        return result;
    }

    private String firstText(ConverMessage message) {
        if (message.getContents() == null) {
            return "";
        }
        for (MessageContent content : message.getContents()) {
            if ("text".equals(content.getType()) && content.getText() != null) {
                return content.getText();
            }
        }
        return "";
    }

    private String safeDeviceName(UserDevice device) {
        return device.getNickname() == null || device.getNickname().isBlank()
                ? device.getDeviceCode()
                : device.getNickname();
    }

    private String safeStatus(String status) {
        return status == null || status.isBlank() ? "离线" : status;
    }

    private String displayName(User user) {
        return firstNonBlank(user.getUsername(), user.getWxNickname(), "未命名");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
