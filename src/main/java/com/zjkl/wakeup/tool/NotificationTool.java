package com.zjkl.wakeup.tool;

import com.zjkl.ai.chat.stomp.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息通知工具
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTool {

    private final ChatPushService chatPushService;
    private final UserStateTool userStateTool;

    /**
     * 发送主动唤醒消息给用户
     *
     * @param userId  用户ID
     * @param content 消息内容
     * @return 是否发送成功
     */
    public boolean sendWakeUpMessage(String userId, String content) {
        try {
            // 1. 检查用户是否在免打扰模式
            if (userStateTool.isDoNotDisturb(userId)) {
                log.debug("用户处于免打扰模式，跳过发送：userId={}", userId);
                return false;
            }

            // 2. 发送系统消息
            chatPushService.pushSystem(userId, content);

            // 3. 记录本次唤醒时间
            userStateTool.recordWakeUp(userId);

            log.info("主动唤醒消息已发送：userId={}, content={}", userId, content);
            return true;
        } catch (Exception e) {
            log.error("发送主动唤醒消息失败：userId={}", userId, e);
            return false;
        }
    }

    /**
     * 发送文本消息（不带唤醒记录）
     *
     * @param userId  用户ID
     * @param content 消息内容
     */
    public void sendTextMessage(String userId, String content) {
        try {
            chatPushService.pushSystem(userId, content);
            log.debug("文本消息已发送：userId={}, content={}", userId, content);
        } catch (Exception e) {
            log.error("发送文本消息失败：userId={}", userId, e);
        }
    }
}
