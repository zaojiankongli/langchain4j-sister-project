package com.zjkl.ai.chat.controller;

import com.zjkl.ai.chat.dto.MessageDTO;
import com.zjkl.ai.chat.dto.SessionPreviewVO;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_OFFSET = 10_000;

    private final ConverMessageService converMessageService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/{userId}")
    public Result<List<MessageDTO>> getHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return endpointMetrics.recordResult("web", "messages.history", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            int safeOffset = Math.max(0, Math.min(offset, MAX_OFFSET));
            int safeLimit = normalizeLimit(limit);
            List<ConverMessage> messages = converMessageService.getHistory(userId, safeOffset, safeLimit);
            List<MessageDTO> dtos = converMessageService.toDTOList(messages);
            return Result.success(dtos);
        });
    }

    @GetMapping("/{userId}/latest")
    public Result<List<MessageDTO>> getLatest(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return endpointMetrics.recordResult("web", "messages.latest", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            int safeLimit = normalizeLimit(limit);
            List<ConverMessage> messages = converMessageService.getLatestMessages(userId, safeLimit);
            List<MessageDTO> dtos = converMessageService.toDTOList(messages);
            return Result.success(dtos);
        });
    }

    @GetMapping("/{userId}/by-date")
    public Result<List<MessageDTO>> getByDate(
            @PathVariable String userId,
            @RequestParam String date) {
        return endpointMetrics.recordResult("web", "messages.by_date", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            try {
                List<ConverMessage> messages = converMessageService.getByDate(userId, date);
                List<MessageDTO> dtos = converMessageService.toDTOList(messages);
                return Result.success(dtos);
            } catch (IllegalArgumentException e) {
                return Result.badRequest("日期格式错误");
            }
        });
    }

    @GetMapping("/{userId}/sessions")
    public Result<List<SessionPreviewVO>> getSessions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return endpointMetrics.recordResult("web", "messages.sessions", () -> {
            int authCode = userContext.checkSelfAccessCode(userId);
            if (authCode != 0) {
                return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
            }
            int safeLimit = normalizeLimit(limit);
            List<SessionPreviewVO> voList = converMessageService.getSessionPreviews(userId, safeLimit);
            return Result.success(voList);
        });
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
