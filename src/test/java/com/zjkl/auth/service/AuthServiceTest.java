package com.zjkl.auth.service;

import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.common.util.JwtUtil;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import com.zjkl.user.service.UserProfileManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.env.Environment;

import java.security.MessageDigest;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private Environment env;

    @Mock
    private UserProfileManageService userProfileManageService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_CODE = "123456";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_USER_ID = "1";

    @BeforeEach
    void setUp() {
        lenient().when(env.getProperty("spring.mail.username")).thenReturn("noreply@example.com");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Manually construct after env mock is set up (env.getProperty called in constructor)
        authService = new AuthService(userMapper, jwtUtil, redisTemplate, mailSender, env, userProfileManageService);
    }

    @Test
    void sendCodeSendsEmailAndStoresCodeInRedis() {
        // Arrange
        doNothing().when(valueOperations).set(any(String.class), any(String.class), eq(5L), eq(TimeUnit.MINUTES));

        // Act
        authService.sendCode(TEST_EMAIL);

        // Assert
        verify(mailSender).send(any(SimpleMailMessage.class));
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("noreply@example.com", sentMessage.getFrom());
        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertTrue(sentMessage.getText().contains("您的验证码是："));
        assertTrue(sentMessage.getText().contains("5 分钟内有效"));

        verify(valueOperations).set(eq("auth:code:" + TEST_EMAIL), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void loginWithValidCodeCreatesTokens() {
        // Arrange
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_CODE, TEST_USERNAME);
        User createdUser = new User();
        createdUser.setId(TEST_USER_ID);
        createdUser.setEmail(TEST_EMAIL);
        createdUser.setUsername(TEST_USERNAME);
        createdUser.setAiType(1); // aiType set so requiresProfileComplete returns false

        when(redisTemplate.execute(any(), anyList(), eq(TEST_CODE))).thenReturn(1L);
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(userMapper.findByEmail(TEST_EMAIL)).thenReturn(null); // New user, no existing record
        when(userProfileManageService.createUser(TEST_EMAIL, TEST_USERNAME)).thenReturn(createdUser);
        when(jwtUtil.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(userProfileManageService.buildUserInfo(any(User.class))).thenReturn(Map.of("id", TEST_USER_ID));

        // Act
        Map<String, Object> result = authService.login(request);

        // Assert
        assertNotNull(result);
        assertEquals("access-token", result.get("accessToken"));
        assertEquals("refresh-token", result.get("refreshToken"));
        assertEquals(Map.of("id", TEST_USER_ID), result.get("user"));
        assertFalse((Boolean) result.get("requiresProfileComplete"));
        assertTrue((Boolean) result.get("isNewUser"));

        verify(redisTemplate).execute(any(), eq(List.of("auth:code:" + TEST_EMAIL)), eq(TEST_CODE));
        verify(userMapper).updateLastActiveAt(TEST_USER_ID);
        verify(userProfileManageService).createUser(TEST_EMAIL, TEST_USERNAME);
    }

    @Test
    void loginShouldReleaseLockByOwnerTokenInsteadOfBlindDelete() {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_CODE, TEST_USERNAME);
        User createdUser = new User();
        createdUser.setId(TEST_USER_ID);
        createdUser.setEmail(TEST_EMAIL);
        createdUser.setUsername(TEST_USERNAME);
        createdUser.setAiType(1);

        when(redisTemplate.execute(any(), anyList(), eq(TEST_CODE))).thenReturn(1L);
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(userMapper.findByEmail(TEST_EMAIL)).thenReturn(null);
        when(userProfileManageService.createUser(TEST_EMAIL, TEST_USERNAME)).thenReturn(createdUser);
        when(jwtUtil.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(userProfileManageService.buildUserInfo(any(User.class))).thenReturn(Map.of("id", TEST_USER_ID));

        authService.login(request);

        verify(redisTemplate, never()).delete("auth:login:lock:" + TEST_EMAIL);
        verify(redisTemplate, atLeast(2)).execute(any(), anyList(), anyString());
    }

    @Test
    void loginWithInvalidCodeThrowsUnauthorizedException() {
        // Arrange
        LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongcode", TEST_USERNAME);
        when(redisTemplate.execute(any(), anyList(), eq("wrongcode"))).thenReturn(0L);

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(request);
        });
        assertEquals("验证码错误", exception.getMessage());
    }

    @Test
    void loginWithExpiredCodeThrowsUnauthorizedException() {
        // Arrange
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_CODE, TEST_USERNAME);
        when(redisTemplate.execute(any(), anyList(), eq(TEST_CODE))).thenReturn(-1L);

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(request);
        });
        assertEquals("验证码已过期，请重新获取", exception.getMessage());
    }

    @Test
    void refreshTokenReturnsNewTokens() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        String userId = TEST_USER_ID;
        User testUser = new User();
        testUser.setId(userId);
        testUser.setEmail(TEST_EMAIL);

        when(redisTemplate.hasKey("auth:token:blacklist:" + sha256(refreshToken))).thenReturn(false);
        when(jwtUtil.parseRefreshToken(refreshToken)).thenReturn(userId);
        when(userMapper.findById(userId)).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("new-refresh-token");

        // Act
        Map<String, Object> result = authService.refreshToken(refreshToken);

        // Assert
        assertNotNull(result);
        assertEquals("new-access-token", result.get("accessToken"));
        assertEquals("new-refresh-token", result.get("refreshToken"));

        verify(redisTemplate).hasKey("auth:token:blacklist:" + sha256(refreshToken));
        verify(jwtUtil).parseRefreshToken(refreshToken);
        verify(userMapper).findById(userId);
    }

    @Test
    void refreshTokenShouldBlacklistOldRefreshTokenAfterRotation() {
        String refreshToken = "valid-refresh-token";
        User testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setEmail(TEST_EMAIL);

        when(redisTemplate.hasKey("auth:token:blacklist:" + sha256(refreshToken))).thenReturn(false);
        when(jwtUtil.parseRefreshToken(refreshToken)).thenReturn(TEST_USER_ID);
        when(userMapper.findById(TEST_USER_ID)).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("new-refresh-token");

        authService.refreshToken(refreshToken);

        verify(valueOperations).set(
                eq("auth:token:blacklist:" + sha256(refreshToken)),
                eq("1"),
                eq(7L * 24 * 3600),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void logoutBlacklistsRefreshToken() {
        // Arrange
        String userId = TEST_USER_ID;
        String refreshToken = "valid-refresh-token";

        // Act
        authService.logout(userId, refreshToken, null);

        // Assert
        verify(valueOperations).set(
                eq("auth:token:blacklist:" + sha256(refreshToken)),
                eq("1"),
                eq(7L * 24 * 3600),
                eq(TimeUnit.SECONDS)
        );
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
