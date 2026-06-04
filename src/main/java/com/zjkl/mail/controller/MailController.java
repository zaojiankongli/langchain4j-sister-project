package com.zjkl.mail.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
import com.zjkl.mail.entity.MailMessage;
import com.zjkl.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 信件/通知 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/mails")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;
    private final UserContext userContext;

    /**
     * 获取当前用户信件列表
     * GET /api/mails
     */
    @GetMapping
    public Result<List<MailMessage>> listMails() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        List<MailMessage> mails = mailService.getMails(userId);
        return Result.success(mails);
    }

    /**
     * 标记单条为已读
     * POST /api/mails/{id}/read
     */
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable String id) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        boolean ok = mailService.markAsRead(id, userId);
        if (!ok) {
            return Result.error(404, "信件不存在");
        }
        return Result.success();
    }

    /**
     * 一键全部已读
     * POST /api/mails/read-all
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        int count = mailService.markAllAsRead(userId);
        log.info("用户 {} 一键已读 {} 封信件", userId, count);
        return Result.success();
    }
}
