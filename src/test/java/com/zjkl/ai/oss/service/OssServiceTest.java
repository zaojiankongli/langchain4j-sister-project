package com.zjkl.ai.oss.service;

import com.aliyun.oss.OSS;
import com.zjkl.ai.oss.config.OssConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OssServiceTest {

    @Mock
    private OSS ossClient;

    @Mock
    private OssConfig ossConfig;

    private OssService ossService;

    @BeforeEach
    void setUp() {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(ossConfig.getEndpoint()).thenReturn("https://oss-cn-test.aliyuncs.com");
        ossService = new OssService(ossClient, ossConfig);
    }

    @Test
    void uploadFromUrl_shouldRejectLoopbackAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> ossService.uploadFromUrl("http://127.0.0.1/test.png", "downloads"));

        verifyNoInteractions(ossClient);
    }

    @Test
    void uploadFromUrl_shouldRejectLocalhostHost() {
        assertThrows(IllegalArgumentException.class,
                () -> ossService.uploadFromUrl("http://localhost/test.png", "downloads"));

        verifyNoInteractions(ossClient);
    }
}
