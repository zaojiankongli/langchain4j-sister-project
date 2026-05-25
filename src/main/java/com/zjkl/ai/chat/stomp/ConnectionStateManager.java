package com.zjkl.ai.chat.stomp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 连接状态管理器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectionStateManager {

    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, BlockingQueue<WebSocketMessage>> userQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();
    private static final int QUEUE_CAPACITY = 100;
    private static final long SEND_TIMEOUT_SECONDS = 30;
    private static final String CONTROL_SUFFIX = "_control";

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    private final ConcurrentHashMap<String, ConnectionState> connectionStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> senderThreads = new ConcurrentHashMap<>();

    private volatile boolean shuttingDown = false;

    private final ScheduledExecutorService senderExecutor = Executors.newScheduledThreadPool(
            10,
            r -> {
                Thread t = Thread.ofVirtual().unstarted(r);
                t.setName("sender-" + r.hashCode());
                return t;
            }
    );


    private static final String CHAT_DESTINATION = "/queue/chat";
    private static final String CONTROL_DESTINATION = "/queue/control";

    @PostConstruct
    public void init() {
        log.info("ConnectionStateManager 初始化（消息队列版）");
    }

    @PreDestroy
    public void shutdown() {
        log.info("ConnectionStateManager 关闭中...");
        shuttingDown = true;

        senderExecutor.shutdown();
        try {
            if (!senderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                senderExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            senderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        senderThreads.values().forEach(Thread::interrupt);
        userQueues.clear();
        connectionStates.clear();
        senderThreads.clear();

        log.info("ConnectionStateManager 已关闭");
    }

    // ==================== 公开接口 ====================

    public boolean isUserConnected(String userId) {
        return connectionStates.get(userId) == ConnectionState.CONNECTED;
    }

    public void pushPeekRequest(String userId, String peekId) {
        Map<String, Object> payload = Map.of("peekId", peekId);
        WebSocketMessage message = new WebSocketMessage(MessageType.PEEK_REQUEST, payload);
        enqueueMessage(userId, message);
    }

    public void pushText(String userId, String content, boolean isComplete) {
        WebSocketMessage message = WebSocketMessage.text(content, isComplete);
        enqueueMessage(userId, message);
    }

    public void pushSystem(String userId, String content) {
        WebSocketMessage message = WebSocketMessage.system(content);
        enqueueMessage(userId, message);
    }

    public void pushError(String userId, String errMsg) {
        WebSocketMessage message = WebSocketMessage.error(errMsg);
        enqueueMessage(userId, message);
    }

    public void pushAuthSuccess(String userId) {
        Map<String, Object> payload = Map.of(
                "success", true,
                "userId", userId,
                "message", "认证成功"
        );
        WebSocketMessage message = new WebSocketMessage(MessageType.SYSTEM, payload);
        enqueueMessage(userId, message);
    }

    public void pushAudio(String userId, byte[] audioData) {
        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        Map<String, Object> payload = Map.of("audioData", base64Audio);
        WebSocketMessage message = new WebSocketMessage(MessageType.AUDIO, payload);
        enqueueMessage(userId, message);
    }

    public void pushPong(String userId) {
        WebSocketMessage message = new WebSocketMessage(MessageType.PONG, Map.of("timestamp", System.currentTimeMillis()));
        enqueueControlMessage(userId, message);
    }

    public void onUserConnected(String userId) {
        log.info("用户连接：userId={}", userId);
        connectionStates.put(userId, ConnectionState.CONNECTED);
        ensureQueueExists(userId);
        ensureSenderStarted(userId);
    }

    public void onUserDisconnected(String userId) {
        log.info("用户断开连接：userId={}", userId);
        connectionStates.put(userId, ConnectionState.DISCONNECTED);
        senderExecutor.schedule(() -> {
            ConnectionState state = connectionStates.get(userId);
            if (state == ConnectionState.DISCONNECTED) {
                clearQueue(userId);
                log.debug("队列已清理：userId={}", userId);
            } else {
                log.debug("用户已重新连接，跳过清理：userId={}", userId);
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void updateActiveTime(String userId) {
        // This method is kept for interface compatibility but does nothing in ConnectionStateManager
        // The actual lastActiveTime management is moved to HeartbeatChecker
    }


    private void ensureQueueExists(String userId) {
        userQueues.computeIfAbsent(userId, k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));
        userQueues.computeIfAbsent(userId + CONTROL_SUFFIX, k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));
    }

    private void ensureSenderStarted(String queueKey) {
        senderThreads.computeIfAbsent(queueKey, k -> {
            log.info("启动发送虚拟线程：queueKey={}", queueKey);
            Thread thread = Thread.ofVirtual()
                    .name("sender-loop-" + queueKey)
                    .unstarted(() -> senderLoop(queueKey));
            thread.start();
            return thread;
        });
    }

    private void enqueueMessage(String userId, WebSocketMessage message) {
        if (shuttingDown) {
            log.warn("服务关闭中，丢弃消息：userId={}", userId);
            return;
        }

        BlockingQueue<WebSocketMessage> queue = userQueues.get(userId);
        if (queue == null) {
            ensureQueueExists(userId);
            queue = userQueues.get(userId);
        }

        if (queue.remainingCapacity() == 0) {
            log.warn("消息队列已满，丢弃最老消息：userId={}", userId);
            queue.poll();
        }

        boolean success = queue.offer(message);
        if (!success) {
            log.error("消息入队失败：userId={}", userId);
        } else {
            log.debug("消息已入队：userId={}, type={}, queueSize={}", userId, message.getType(), queue.size());
        }

        ensureSenderStarted(userId);
    }

    private void enqueueControlMessage(String userId, WebSocketMessage message) {
        BlockingQueue<WebSocketMessage> queue = userQueues.computeIfAbsent(userId + CONTROL_SUFFIX,
                k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));

        if (queue.remainingCapacity() == 0) {
            queue.poll();
        }

        queue.offer(message);
        ensureSenderStarted(userId + CONTROL_SUFFIX);
    }

    private void senderLoop(String queueKey) {
        String userId = queueKey.replace(CONTROL_SUFFIX, "");
        boolean isControlQueue = queueKey.endsWith(CONTROL_SUFFIX);
        String destination = isControlQueue ? CONTROL_DESTINATION : CHAT_DESTINATION;

        log.info("senderLoop 开始运行：queueKey={}", queueKey);

        try {
            while (!shuttingDown) {
                BlockingQueue<WebSocketMessage> queue = userQueues.get(queueKey);
                if (queue == null) {
                    break;
                }

                WebSocketMessage message;
                try {
                    message = queue.poll(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (message == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                log.info("senderLoop 获取到消息: queueKey={}, type={}", queueKey, message.getType());

                Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
                boolean sendSuccess = false;
                synchronized (lock) {
                    ConnectionState state = connectionStates.get(userId);
                    if (state == ConnectionState.DISCONNECTED) {
                        log.info("用户已断开，丢弃消息: userId={}", userId);
                        sendSuccess = true;
                        continue;
                    }

                    try {
                        sendMessage(userId, destination, message);
                        sendSuccess = true;
                    } catch (Exception e) {
                        log.error("发送消息失败：userId={}, type={}", userId, message.getType(), e);
                    }
                }

                if (!sendSuccess) {
                    if (!queue.offer(message)) {
                        log.error("消息放回队列失败：userId={}", userId);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            log.debug("发送线程退出：queueKey={}", queueKey);
            senderThreads.remove(queueKey);
        }
    }

    private void sendMessage(String userId, String destination, WebSocketMessage message) {
        log.info("发送消息: userId={}, destination={}, type={}", userId, destination, message.getType());
        messagingTemplate.convertAndSendToUser(userId, destination, message);
        log.info("消息已发送: userId={}, destination={}, type={}", userId, destination, message.getType());
    }

    private void clearQueue(String queueKey) {
        BlockingQueue<WebSocketMessage> queue = userQueues.remove(queueKey);
        if (queue != null) {
            int size = queue.size();
            log.debug("清空未发送消息：queueKey={}, count={}", queueKey, size);
        }
        userLocks.remove(queueKey.replace(CONTROL_SUFFIX, ""));
        Thread thread = senderThreads.remove(queueKey);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }
}