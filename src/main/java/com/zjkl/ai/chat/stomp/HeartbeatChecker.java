package com.zjkl.ai.chat.stomp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心跳检查器
 * <p>
 * 通过 Spring 事件机制解耦：超时断连事件通过 ApplicationEventPublisher 发布，
 * 由 ConnectionStateManager 监听处理，避免直接依赖和循环依赖风险。
 */
@Component
@Slf4j
public class HeartbeatChecker {

    /**
     * 用户心跳超时断连事件
     */
    public record UserDisconnectedEvent(String userId) {}

    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<String, Long> lastActiveTime = new ConcurrentHashMap<>();

    public HeartbeatChecker(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        log.info("HeartbeatChecker 初始化");
    }

    @PreDestroy
    public void shutdown() {
        log.info("HeartbeatChecker 关闭中...");
        log.info("HeartbeatChecker 已关闭");
    }

    // ==================== 公开接口 ====================

    public void updateActiveTime(String userId) {
        lastActiveTime.put(userId, System.currentTimeMillis());
    }

    public void clearActiveTime(String userId) {
        lastActiveTime.remove(userId);
    }

    // Heartbeat check runs every 30 seconds
    @Scheduled(fixedDelay = 30000)
    public void checkHeartbeats() {
        try {
            List<String> userIds = new ArrayList<>(lastActiveTime.keySet());
            for (String userId : userIds) {
                Long lastTime = lastActiveTime.get(userId);
                if (lastTime == null) continue;
                if (System.currentTimeMillis() - lastTime > 90000) {
                    log.warn("用户心跳超时：userId={}", userId);
                    lastActiveTime.remove(userId);
                    // 通过事件发布解耦，不再直接调用 connectionStateManager
                    eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
                }
            }
        } catch (Exception e) {
            log.error("心跳检测失败", e);
        }
    }
}
