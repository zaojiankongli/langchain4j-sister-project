package com.zjkl.ai.chat.service;

import com.zjkl.ai.chat.dto.MessageDTO;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.mapper.ConverMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConverMessageService {

    private final ConverMessageMapper converMessageMapper;

    /**
     * 保存单条消息
     */
    public ConverMessage saveMessage(String userId, String role, List<MessageContent> contents) {
        ConverMessage message = ConverMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .role(role)
                .contents(contents)
                .createdAt(LocalDateTime.now())
                .build();
        converMessageMapper.insert(message);
        return message;
    }

    /**
     * 批量保存消息
     */
    public int batchSaveMessages(List<ConverMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        // 确保每个消息都有 ID
        for (ConverMessage msg : messages) {
            if (msg.getId() == null) {
                msg.setId(UUID.randomUUID().toString());
            }
            if (msg.getCreatedAt() == null) {
                msg.setCreatedAt(LocalDateTime.now());
            }
        }
        return converMessageMapper.batchInsert(messages);
    }

    /**
     * 查询用户消息历史
     */
    @Transactional(readOnly = true)
    public List<ConverMessage> getHistory(String userId, int offset, int limit) {
        return converMessageMapper.selectByUserId(userId, offset, limit);
    }

    /**
     * 查询用户最近的 N 条消息
     */
    @Transactional(readOnly = true)
    public List<ConverMessage> getLatestMessages(String userId, int limit) {
        return converMessageMapper.selectLatestByUserId(userId, limit);
    }

    /**
     * 查询指定日期的消息
     */
    @Transactional(readOnly = true)
    public List<ConverMessage> getByDate(String userId, String date) {
        LocalDateTime startTime = LocalDate.parse(date).atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        return converMessageMapper.selectByUserIdAndTimeRange(userId, startTime, endTime);
    }

    /**
     * 按时间范围查询消息（用于情绪模块，避免跨模块 mapper 访问）
     */
    @Transactional(readOnly = true)
    public List<ConverMessage> getByTimeRange(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        return converMessageMapper.selectByUserIdAndTimeRange(userId, startTime, endTime);
    }

    /**
     * 将实体列表转换为 DTO 列表
     */
    public List<MessageDTO> toDTOList(List<ConverMessage> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        return messages.stream()
                .map(m -> MessageDTO.fromEntity(
                        m.getId(),
                        m.getRole(),
                        m.getCreatedAt(),
                        m.getContents()))
                .toList();
    }
}
