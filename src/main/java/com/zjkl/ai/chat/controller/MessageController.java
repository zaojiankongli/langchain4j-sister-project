package com.zjkl.ai.chat.controller;

import com.zjkl.ai.chat.dto.MessageDTO;
import com.zjkl.ai.chat.dto.SessionPreviewVO;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器
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

    /**
     * 查询用户消息历史
     *
     */
    @GetMapping("/{userId}")
    public Result<List<MessageDTO>> getHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        offset = Math.max(0, Math.min(offset, MAX_OFFSET));
        limit = normalizeLimit(limit);
        List<ConverMessage> messages = converMessageService.getHistory(userId, offset, limit);
        List<MessageDTO> dtos = converMessageService.toDTOList(messages);

        return Result.success(dtos);
    }

    /**
     * 查询用户最近的 N 条消息
     *
     */
    @GetMapping("/{userId}/latest")
    public Result<List<MessageDTO>> getLatest(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {

        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        limit = normalizeLimit(limit);
        List<ConverMessage> messages = converMessageService.getLatestMessages(userId, limit);
        List<MessageDTO> dtos = converMessageService.toDTOList(messages);

        return Result.success(dtos);
    }

    /**
     * 查询指定日期的消息
     *
     */
    @GetMapping("/{userId}/by-date")
    public Result<List<MessageDTO>> getByDate(
            @PathVariable String userId,
            @RequestParam String date) {

        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        List<ConverMessage> messages;
        try {
            messages = converMessageService.getByDate(userId, date);
        } catch (IllegalArgumentException e) {
            return Result.badRequest("日期格式错误");
        }
        List<MessageDTO> dtos = converMessageService.toDTOList(messages);
        return Result.success(dtos);
    }

    /**
     * 查询用户会话摘要
     *
     */
    @GetMapping("/{userId}/sessions")
    public Result<List<SessionPreviewVO>> getSessions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {

        int authCode = userContext.checkSelfAccessCode(userId);
        if (authCode != 0) {
            return Result.error(authCode, authCode == 401 ? "请先登录" : "无权访问");
        }
        limit = normalizeLimit(limit);
        List<SessionPreviewVO> voList = converMessageService.getSessionPreviews(userId, limit);

        return Result.success(voList);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
