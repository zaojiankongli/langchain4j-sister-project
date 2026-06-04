package com.zjkl.common.util;

import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.user.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    @Test
    void generateAccessToken_shouldFailFastWhenSecretIsShort() {
        AuthProperties properties = new AuthProperties();
        properties.setSecret("short-secret");
        properties.setAccessTokenExpiration(7_200_000L);
        properties.setRefreshTokenExpiration(604_800_000L);
        JwtUtil jwtUtil = new JwtUtil(properties);
        User user = new User();
        user.setId("u1");
        user.setEmail("u1@example.com");
        user.setUsername("u1");

        assertThrows(IllegalStateException.class, () -> jwtUtil.generateAccessToken(user));
    }
}
