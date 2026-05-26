package com.zjkl.ai.chat.stomp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 心跳检查器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HeartbeatChecker {

    private final ConnectionStateManager connectionStateManager;
    private final ConcurrentHashMap<String, Long> lastActiveTime = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("HeartbeatChecker 初始化");
        startHeartbeatChecker();
    }

    @PreDestroy
    public void shutdown() {
        log.info("HeartbeatChecker 关闭中...");
        // Note: The actual shutdown logic is handled by the scheduled executor in ConnectionStateManager
        // Since we're using @Scheduled, Spring will handle the cleanup
        log.info("HeartbeatChecker 已关闭");
    }

    // ==================== 公开接口 ====================

    public void updateActiveTime(String userId) {
        lastActiveTime.put(userId, System.currentTimeMillis());
    }

    private void startHeartbeatChecker() {
        // Using @Scheduled instead of manual ScheduledExecutorService
        // This method is kept for compatibility but the actual scheduling is done via @Scheduled annotation
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
                    connectionStateManager.onUserDisconnected(userId);
                }
            }
        } catch (Exception e) {
            log.error("心跳检测失败", e);
        }
    }
}