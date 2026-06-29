package com.zjkl.mail.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.mail.entity.MailMessage;
import com.zjkl.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 信件/通知 REST API。
 */
@Slf4j
@RestController
@RequestMapping("/api/mails")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping
    public Result<List<MailMessage>> listMails() {
        return endpointMetrics.recordResult("desktop", "mail.list", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            List<MailMessage> mails = mailService.getMails(userId);
            return Result.success(mails);
        });
    }

    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable String id) {
        return endpointMetrics.recordResult("desktop", "mail.mark_read", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            boolean ok = mailService.markAsRead(id, userId);
            if (!ok) {
                return Result.error(404, "信件不存在");
            }
            return Result.success();
        });
    }

    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        return endpointMetrics.recordResult("desktop", "mail.mark_all_read", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            int count = mailService.markAllAsRead(userId);
            log.info("用户 {} 一键已读 {} 封信件", userId, count);
            return Result.success();
        });
    }
}
