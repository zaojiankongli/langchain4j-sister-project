package com.zjkl.user.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserContext userContext;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private MultipartFile file;

    private UserProfileController userProfileController;

    @BeforeEach
    void setUp() {
        userProfileController = new UserProfileController(userProfileService, userContext, rateLimiter);
    }

    @Test
    void getProfile_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = userProfileController.getProfile();

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
        verify(userProfileService, never()).getProfile(null);
    }

    @Test
    void uploadAvatar_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = userProfileController.uploadAvatar(file);

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
        verify(rateLimiter, never()).tryAcquire("rate:avatar:null", 3, 60_000L);
        verify(userProfileService, never()).uploadAvatar(null, file);
    }
}
