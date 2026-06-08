package com.zjkl.emotion.listener;

import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.anchor.model.AnchorEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionEventListenerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;

    @Test
    void onAnchorEnded_shouldInjectAnchorSummaryIntoChatHistory() {
        EmotionEventListener listener = new EmotionEventListener(stringRedisTemplate);

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);

        AnchorEvent event = AnchorEvent.builder()
                .id(1L)
                .userId("u1")
                .eventTitle("有趣的对话")
                .summary("这是一次被注入到历史中的锚点摘要")
                .endType(AnchorEvent.EndType.NEGATIVE)
                .durationSeconds(120)
                .build();

        listener.onAnchorEnded(new AnchorEndedEvent("u1", event, event.getEndType(), LocalDateTime.now()));

        verify(listOperations).rightPush(anyString(), anyString());
        verify(listOperations).trim("chat:history:u1", -200, -1);
    }
}
