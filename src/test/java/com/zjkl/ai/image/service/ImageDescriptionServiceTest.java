package com.zjkl.ai.image.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.zjkl.common.config.properties.AiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageDescriptionServiceTest {

    @Mock
    private AiProperties aiProperties;
    @Mock
    private MultiModalConversation conversation;
    @Mock
    private MultiModalConversationResult result;
    @Mock
    private MultiModalConversationOutput output;

    private ImageDescriptionService service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new ImageDescriptionService(aiProperties);
        ReflectionTestUtils.setField(service, "conversation", conversation);
        logger = (Logger) LoggerFactory.getLogger(ImageDescriptionService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void describe_shouldNotLogFullImageUrl() throws Exception {
        String imageUrl = "https://example.com/private/image.png?token=secret";
        MultiModalConversationOutput.Choice choice = mock(MultiModalConversationOutput.Choice.class);
        MultiModalMessage message = mock(MultiModalMessage.class);

        when(aiProperties.getVisionModelName()).thenReturn("qwen-vl");
        when(aiProperties.getVisionApiKey()).thenReturn("api-key");
        when(conversation.call(any())).thenReturn(result);
        when(result.getOutput()).thenReturn(output);
        when(output.getChoices()).thenReturn(List.of(choice));
        when(choice.getMessage()).thenReturn(message);
        when(message.getContent()).thenReturn(List.of(Map.of("text", "一张图片")));

        String description = service.describe(imageUrl);

        assertEquals("一张图片", description);

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);

        assertFalse(logs.contains(imageUrl), "日志不应包含完整图片 URL");
    }
}
