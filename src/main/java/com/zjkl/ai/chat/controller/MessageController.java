package com.zjkl.ai.chat.controller;

import com.zjkl.ai.chat.dto.MessageDTO;
import com.zjkl.ai.chat.dto.SessionPreviewVO;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.mapper.ConverMessageMapper;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.user.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ConverMessageService converMessageService;
    private final ConverMessageMapper converMessageMapper;

    /**
     * 查询用户消息历史
     *
     */
    @GetMapping("/{userId}")
    public Result<List<MessageDTO>> getHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

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

        List<ConverMessage> messages = converMessageService.getByDate(userId, date);
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

        List<Map<String, Object>> rows = converMessageMapper.selectSessionPreviews(userId, limit);
        List<SessionPreviewVO> voList = rows.stream().map(row -> {
            SessionPreviewVO vo = new SessionPreviewVO();
            Object dateVal = row.get("date");
            vo.setDate(dateVal != null ? dateVal.toString() : null);
            Object previewVal = row.get("preview_text");
            String preview = previewVal != null ? previewVal.toString() : "";
            if (preview.length() >= 50) {
                preview = preview.substring(0, 50) + "...";
            }
            vo.setPreview(preview);
            return vo;
        }).toList();

        return Result.success(voList);
    }
}
