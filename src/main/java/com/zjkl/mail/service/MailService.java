package com.zjkl.mail.service;

import com.zjkl.mail.entity.MailMessage;
import com.zjkl.mail.mapper.MailMessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 信件服务 — 管理用户系统通知/信件
 * <p>
 * 数据流向：
 *   read:  Redis → MySQL（无本地缓存，邮件量小且读取不频繁）
 *   write: MySQL → 删除 Redis 缓存
 */
@Slf4j
@Service
public class MailService {

    private static final String MAIL_CACHE_KEY_PREFIX = "user:mails:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(5);

    private final MailMessageMapper mailMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MailService(MailMessageMapper mailMapper, StringRedisTemplate redisTemplate) {
        this.mailMapper = mailMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 获取用户信件列表（Redis → MySQL，按时间降序）
     */
    public List<MailMessage> getMails(String userId) {
        // 1. Redis 缓存
        String cached = redisTemplate.opsForValue().get(MAIL_CACHE_KEY_PREFIX + userId);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<MailMessage>>() {});
            } catch (Exception e) {
                log.warn("解析 Redis 邮件缓存失败: userId={}", userId, e);
            }
        }

        // 2. MySQL 持久层
        List<MailMessage> mails = mailMapper.selectByUserId(userId);
        if (mails == null || mails.isEmpty()) {
            // 新用户 → 插入欢迎信件
            mailMapper.insertWelcomeMails(userId);
            mails = mailMapper.selectByUserId(userId);
        }

        // 3. 回写 Redis
        saveToRedis(userId, mails);
        return mails;
    }

    /**
     * 标记单条为已读
     */
    public boolean markAsRead(String mailId, String userId) {
        int rows = mailMapper.markAsRead(mailId, userId);
        if (rows > 0) {
            redisTemplate.delete(MAIL_CACHE_KEY_PREFIX + userId);
            return true;
        }
        return false;
    }

    /**
     * 一键全部已读
     */
    public int markAllAsRead(String userId) {
        int count = mailMapper.markAllAsRead(userId);
        if (count > 0) {
            redisTemplate.delete(MAIL_CACHE_KEY_PREFIX + userId);
        }
        return count;
    }

    /**
     * 添加新信件（由 MailScheduler 或其他模块调用）
     */
    public MailMessage addMail(String userId, String tag, String subject, String excerpt) {
        MailMessage mail = new MailMessage();
        mail.setId(UUID.randomUUID().toString());
        mail.setUserId(userId);
        mail.setTag(tag);
        mail.setSubject(subject);
        mail.setExcerpt(excerpt);
        mail.setRead(false);
        mail.setCreatedAt(LocalDateTime.now());

        mailMapper.insert(mail);
        redisTemplate.delete(MAIL_CACHE_KEY_PREFIX + userId);

        log.info("新信件 → userId={} tag={} subject={}", userId, tag, subject);
        return mail;
    }

    private void saveToRedis(String userId, List<MailMessage> mails) {
        try {
            String json = objectMapper.writeValueAsString(mails);
            redisTemplate.opsForValue().set(MAIL_CACHE_KEY_PREFIX + userId, json, REDIS_TTL);
        } catch (Exception e) {
            log.warn("写入 Redis 邮件缓存失败: userId={}", userId, e);
        }
    }
}
