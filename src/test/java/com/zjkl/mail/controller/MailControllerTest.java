package com.zjkl.mail.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.mail.service.MailService;
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
class MailControllerTest {

    @Mock
    private MailService mailService;

    @Mock
    private UserContext userContext;

    private MailController mailController;

    @BeforeEach
    void setUp() {
        mailController = new MailController(mailService, userContext);
    }

    @Test
    void markAsRead_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = mailController.markAsRead("mail-1");

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
        verify(mailService, never()).markAsRead("mail-1", null);
    }

    @Test
    void markAllAsRead_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        Result<?> result = mailController.markAllAsRead();

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
        verify(mailService, never()).markAllAsRead(null);
    }
}
