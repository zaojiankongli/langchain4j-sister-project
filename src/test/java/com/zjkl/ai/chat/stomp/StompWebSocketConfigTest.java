package com.zjkl.ai.chat.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StompWebSocketConfigTest {

    @Test
    void configureWebSocketTransport_shouldApplyMessageLimits() throws Exception {
        Class<?> propertiesClass = Class.forName("com.zjkl.common.config.properties.WebSocketProperties");
        Object properties = propertiesClass.getDeclaredConstructor().newInstance();

        Method setMessageSizeLimit = propertiesClass.getMethod("setMessageSizeLimit", int.class);
        Method setSendBufferSizeLimit = propertiesClass.getMethod("setSendBufferSizeLimit", int.class);
        setMessageSizeLimit.invoke(properties, 131_072);
        setSendBufferSizeLimit.invoke(properties, 262_144);

        Class<?> jwtUtilClass = Class.forName("com.zjkl.common.util.JwtUtil");
        Class<?> redisTemplateClass = Class.forName("org.springframework.data.redis.core.StringRedisTemplate");

        Constructor<StompWebSocketConfig> constructor = StompWebSocketConfig.class.getConstructor(
                jwtUtilClass,
                propertiesClass,
                redisTemplateClass
        );

        StompWebSocketConfig config = constructor.newInstance(
                mock(jwtUtilClass),
                properties,
                mock(redisTemplateClass)
        );

        WebSocketTransportRegistration registration = new WebSocketTransportRegistration();
        config.configureWebSocketTransport(registration);

        assertEquals(131_072, ReflectionTestUtils.getField(registration, "messageSizeLimit"));
        assertEquals(262_144, ReflectionTestUtils.getField(registration, "sendBufferSizeLimit"));
    }
}
