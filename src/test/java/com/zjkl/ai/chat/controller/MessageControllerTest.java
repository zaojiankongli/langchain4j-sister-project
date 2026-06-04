package com.zjkl.ai.chat.controller;

import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private ConverMessageService converMessageService;

    @Mock
    private UserContext userContext;

    private MessageController messageController;

    @BeforeEach
    void setUp() {
        messageController = new MessageController(converMessageService, userContext);
    }

    @Test
    void getHistory_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.checkSelfAccessCode(anyString())).thenReturn(401);

        Result<?> result = messageController.getHistory("u1", 0, 20);

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }
}
