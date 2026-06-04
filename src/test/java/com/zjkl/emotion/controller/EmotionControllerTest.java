package com.zjkl.emotion.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.emotion.service.EmotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionControllerTest {

    @Mock
    private EmotionService emotionService;
    @Mock
    private UserContext userContext;

    private EmotionController emotionController;

    @BeforeEach
    void setUp() {
        emotionController = new EmotionController(emotionService, userContext);
    }

    @Test
    void getEmotion_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.checkSelfAccessCode(anyString())).thenReturn(401);

        Result<?> result = emotionController.getEmotion("u1");

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }
}
