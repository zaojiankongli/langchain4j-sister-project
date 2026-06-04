package com.zjkl.ai.image.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.AiProperties;
import com.zjkl.user.util.HttpClientUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WanxImageServiceTest {

    @Mock
    private HttpClientUtil httpClientUtil;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private AiProperties aiProperties;

    private WanxImageService service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new WanxImageService(httpClientUtil, promptTemplateService, new ObjectMapper(), aiProperties);
        logger = (Logger) LoggerFactory.getLogger(WanxImageService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void generate_shouldNotLogFullPromptWhenSubmissionFails() throws Exception {
        ImageElements elements = ImageElements.builder()
                .clothing("校服")
                .scene("教室")
                .timeOfDay("上午")
                .atmosphere("温馨")
                .emotion("开心")
                .keyProps(List.of("秘密道具"))
                .build();
        String sensitivePrompt = "SECRET-PROMPT-DO-NOT-LOG";

        when(promptTemplateService.render(anyString(), any())).thenReturn(sensitivePrompt);
        when(aiProperties.getChatApiKey()).thenReturn("api-key");
        when(aiProperties.getWanxReferenceImageUrl()).thenReturn("https://example.com/reference.png");
        when(httpClientUtil.post(anyString(), anyMap(), anyString())).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> service.generate(elements));

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);

        assertFalse(logs.contains(sensitivePrompt), "日志不应包含完整 prompt 内容");
    }
}
