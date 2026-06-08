package com.zjkl.emotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.anchor.service.AnchorSemanticService;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.anchor.model.AnchorEvent;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnchorSemanticServiceTest {

    @Mock
    private QwenChatModel qwenChatModel;
    @Mock
    private ConverMessageService converMessageService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void generateSemanticFields_shouldRequestOnlyLatestTwentyMessagesInRange() {
        AnchorSemanticService service = new AnchorSemanticService(
                qwenChatModel,
                converMessageService,
                stringRedisTemplate,
                new ObjectMapper()
        );

        AnchorEvent event = new AnchorEvent();
        event.setUserId("u1");
        event.setStartTime(LocalDateTime.now().minusMinutes(10));

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"eventTitle\":\"标题\",\"triggerBehavior\":\"行为\",\"highlightTraits\":\"特质\",\"summary\":\"摘要\",\"endReason\":\"结束\",\"aiReflection\":\"反思\"}"))
                .build();

        when(qwenChatModel.chat(any(ChatRequest.class))).thenReturn(response);
        when(converMessageService.getLatestByTimeRange(any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        service.generateSemanticFields(event);

        verify(converMessageService).getLatestByTimeRange(eq("u1"), eq(event.getStartTime()), any(LocalDateTime.class), eq(AnchorSemanticService.CONTEXT_MESSAGE_LIMIT));
        verify(converMessageService, never()).getByTimeRange(any(), any(), any());
    }
}
