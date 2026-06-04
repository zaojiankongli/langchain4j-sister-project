package com.zjkl.settings.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.settings.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserContext userContext;

    private SettingsController settingsController;

    @BeforeEach
    void setUp() {
        settingsController = new SettingsController(settingsService, userContext);
    }

    @Test
    void getSettings_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.checkSelfAccessCode(anyString())).thenReturn(401);

        Result<?> result = settingsController.getSettings("u1");

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }
}
