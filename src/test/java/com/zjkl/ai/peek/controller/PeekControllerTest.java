package com.zjkl.ai.peek.controller;

import com.zjkl.ai.oss.service.OssService;
import com.zjkl.ai.peek.service.PeekCallbackService;
import com.zjkl.common.Result;
import com.zjkl.common.config.properties.PeekProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PeekControllerTest {

    @Test
    void handleScreenshotCallback_shouldNotExposeInternalExceptionMessage() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        OssService ossService = mock(OssService.class);
        PeekProperties peekProperties = new PeekProperties();
        PeekController controller = new PeekController(
                redisTemplate,
                ossService,
                mock(PeekCallbackService.class),
                peekProperties
        );
        MockMultipartFile screenshot = new MockMultipartFile("screenshot", "a.png", "image/png", new byte[]{1});
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("peek:pending:peek-1")).thenReturn("u1");
        when(ossService.uploadFile("peek", screenshot)).thenThrow(new RuntimeException("internal bucket secret"));

        Result<Map<String, Object>> result = controller.handleScreenshotCallback("peek-1", screenshot);

        assertEquals(500, result.getCode());
        assertEquals("截图上传失败，请稍后重试", result.getMessage());
        assertFalse(result.getMessage().contains("internal bucket secret"), "500 response must not expose internal exception details");
    }
}
