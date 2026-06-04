package com.zjkl.memory.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.memory.service.MemoryQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryControllerTest {

    @Mock
    private MemoryQueryService memoryQueryService;
    @Mock
    private UserContext userContext;

    private MemoryController memoryController;

    @BeforeEach
    void setUp() {
        memoryController = new MemoryController(memoryQueryService, userContext);
    }

    @Test
    void list_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = memoryController.list(1, 5, null, false);

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }
}
