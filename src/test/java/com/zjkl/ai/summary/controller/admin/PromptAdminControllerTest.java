package com.zjkl.ai.summary.controller.admin;

import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.Result;
import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.context.UserContext;
import com.zjkl.memory.service.PromptCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptAdminControllerTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private PromptCacheService promptCacheService;
    @Mock
    private UserContext userContext;
    @Mock
    private AuthProperties authProperties;

    private PromptAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PromptAdminController(promptTemplateService, promptCacheService, userContext, authProperties);
    }

    @Test
    void listTemplates_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.checkAdminAccess(authProperties)).thenReturn("请先登录");

        Result<?> result = controller.listTemplates();

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }

    @Test
    void listTemplates_shouldReturnForbiddenWhenNotAdmin() {
        when(userContext.checkAdminAccess(authProperties)).thenReturn("需要管理员权限");

        Result<?> result = controller.listTemplates();

        assertEquals(401, result.getCode());
        assertEquals("需要管理员权限", result.getMessage());
    }
}
