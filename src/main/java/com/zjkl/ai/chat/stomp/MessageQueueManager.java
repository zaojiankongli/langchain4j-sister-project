package com.zjkl.ai.chat.stomp;

import com.zjkl.ai.chat.stomp.dto.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 消息队列管理器
 * 管理每个用户的聊天队列和控制队列
 */
@Component
@Slf4j
public class MessageQueueManager {

    private final ConcurrentHashMap<String, BlockingQueue<WebSocketMessage>> userQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private static final int QUEUE_CAPACITY = 100;
    public static final String CONTROL_SUFFIX = "_control";

    /**
     * 确保用户的聊天队列和控制队列都存在
     */
    public void ensureQueuesExist(String userId) {
        userQueues.computeIfAbsent(userId, k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));
        userQueues.computeIfAbsent(userId + CONTROL_SUFFIX, k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));
    }

    /**
     * 向用户的聊天队列放入消息
     */
    public boolean offerToChatQueue(String userId, WebSocketMessage message) {
        BlockingQueue<WebSocketMessage> queue = userQueues.get(userId);
        if (queue == null) {
            ensureQueuesExist(userId);
            queue = userQueues.get(userId);
        }
        return offerWithCapacityCheck(queue, message, userId);
    }

    /**
     * 向用户的控制队列放入消息
     */
    public boolean offerToControlQueue(String userId, WebSocketMessage message) {
        BlockingQueue<WebSocketMessage> queue = userQueues.computeIfAbsent(
                userId + CONTROL_SUFFIX, k -> new LinkedBlockingQueue<>(QUEUE_CAPACITY));
        return offerWithCapacityCheck(queue, message, userId);
    }

    private boolean offerWithCapacityCheck(BlockingQueue<WebSocketMessage> queue, WebSocketMessage message, String userId) {
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
        return success;
    }

    /**
     * 获取指定队列
     */
    public BlockingQueue<WebSocketMessage> getQueue(String queueKey) {
        return userQueues.get(queueKey);
    }

    /**
     * 获取用户锁
     */
    public ReentrantLock getLock(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }

    /**
     * 移除并返回队列大小
     */
    public int clearAndRemoveQueue(String queueKey) {
        BlockingQueue<WebSocketMessage> queue = userQueues.remove(queueKey);
        if (queue != null) {
            int size = queue.size();
            log.debug("清空未发送消息：queueKey={}, count={}", queueKey, size);
            return size;
        }
        return 0;
    }

    /**
     * 检查队列是否存在
     */
    public boolean hasQueue(String queueKey) {
        return userQueues.containsKey(queueKey);
    }

    /**
     * 清空所有队列
     */
    public void clearAllQueues() {
        userQueues.clear();
    }
}
