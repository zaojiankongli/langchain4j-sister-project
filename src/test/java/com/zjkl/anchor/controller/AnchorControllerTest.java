package com.zjkl.anchor.controller;

import com.zjkl.anchor.service.AnchorService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnchorControllerTest {

    @Mock
    private AnchorService anchorService;

    @Mock
    private UserContext userContext;

    private AnchorController anchorController;

    @BeforeEach
    void setUp() {
        anchorController = new AnchorController(anchorService, userContext);
    }

    @Test
    void list_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = anchorController.list(1, 5, null);

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
        verify(anchorService, never()).getMilestones(null, 0, 5, null, null);
    }
}
