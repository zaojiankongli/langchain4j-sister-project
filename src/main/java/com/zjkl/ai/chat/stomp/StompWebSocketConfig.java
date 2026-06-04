package com.zjkl.ai.chat.stomp;

import com.zjkl.common.config.properties.WebSocketProperties;
import com.zjkl.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket 配置类
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String STOMP_AUTH_FAILED_MESSAGE = "认证失败";
    private static final String STOMP_ACCESS_DENIED_MESSAGE = "无权访问";

    private final JwtUtil jwtUtil;
    private final WebSocketProperties webSocketProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";

    public StompWebSocketConfig(JwtUtil jwtUtil, WebSocketProperties webSocketProperties,
                                StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.webSocketProperties = webSocketProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins().toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // 应用目标前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 用户目标前缀
        registry.setUserDestinationPrefix("/user");
        // 启用简单内存
        registry.enableSimpleBroker("/queue", "/topic");
    }

    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(webSocketProperties.getMessageSizeLimit());
        registration.setSendBufferSizeLimit(webSocketProperties.getSendBufferSizeLimit());
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 从 Native Headers 获取 token
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    String token = null;
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    }

                    if (token == null || token.isEmpty()) {
                        log.warn("STOMP CONNECT 认证失败：缺少 Token");
                        throw new IllegalArgumentException(STOMP_AUTH_FAILED_MESSAGE);
                    }

                    try {
                        // 检查 access token 是否已被吊销（黑名单）
                        if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token))) {
                            log.warn("STOMP CONNECT 认证失败：Token 已被吊销");
                            throw new IllegalArgumentException(STOMP_AUTH_FAILED_MESSAGE);
                        }

                        String userId = jwtUtil.parseAccessToken(token);
                        if (userId == null || userId.isEmpty()) {
                            log.warn("STOMP CONNECT 认证失败：Token 无效或已过期");
                            throw new IllegalArgumentException(STOMP_AUTH_FAILED_MESSAGE);
                        }

                        accessor.setUser(new java.security.Principal() {
                            @Override
                            public String getName() {
                                return userId;
                            }
                        });

                        log.info("STOMP CONNECT 认证成功：userId={}", userId);
                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (Exception e) {
                        log.warn("STOMP CONNECT 认证异常：{}", e.getMessage());
                        throw new IllegalArgumentException(STOMP_AUTH_FAILED_MESSAGE);
                    }
                }
                // SUBSCRIBE 授权：防止用户订阅其他用户的解析后队列
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    java.security.Principal user = accessor.getUser();
                    if (destination != null && user != null) {
                        // 检查直接订阅解析后的目标：/queue/chat-userXXX 或 /queue/control-userXXX
                        int userIdx = destination.indexOf("-user");
                        if (userIdx >= 0) {
                            String targetUserId = destination.substring(userIdx + 5);
                            if (!targetUserId.isEmpty() && !targetUserId.equals(user.getName())) {
                                log.warn("STOMP SUBSCRIBE 越权拦截：userId={} 试图订阅 {}",
                                        user.getName(), destination);
                                throw new IllegalArgumentException(STOMP_ACCESS_DENIED_MESSAGE);
                            }
                        }
                        // 也检查原始 /user/ 前缀目标
                        if (destination.startsWith("/user/")) {
                            String[] parts = destination.split("/");
                            if (parts.length >= 3) {
                                String embedded = parts[2];
                                if (!"queue".equals(embedded) && !"topic".equals(embedded)
                                        && !embedded.equals(user.getName())) {
                                    log.warn("STOMP SUBSCRIBE 越权拦截：userId={} 试图订阅 /user/{}/...",
                                            user.getName(), embedded);
                                    throw new IllegalArgumentException(STOMP_ACCESS_DENIED_MESSAGE);
                                }
                            }
                        }
                    }
                }
                return message;
            }
        });
    }

    /**
     * 出站通道拦截器（保留用于未来扩展）
     * <p>
     * SUBSCRIBE 授权已移至 inbound channel（outbound 看到的已是 Spring 解析后的目标）。
     */
    @Override
    public void configureClientOutboundChannel(@NonNull ChannelRegistration registration) {
        // 保留用于未来出站消息处理需求（如审计、脱敏）
    }
}
