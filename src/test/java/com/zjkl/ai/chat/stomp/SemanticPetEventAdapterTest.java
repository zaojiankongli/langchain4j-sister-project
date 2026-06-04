package com.zjkl.ai.chat.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SemanticPetEventAdapterTest {

    @Mock
    private ChatPushService chatPushService;

    private SemanticPetEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SemanticPetEventAdapter(chatPushService);
    }

    @Test
    void pushChatPhase_shouldEmitSemanticMotion() {
        adapter.pushChatPhase("u1", SemanticPetEventAdapter.ChatPhase.THINKING);

        verify(chatPushService).pushPetMotion("u1", "thinking", "normal");
    }

    @Test
    void pushMoodExpression_shouldMapChineseMoodToSemanticExpression() {
        adapter.pushMoodExpression("u1", "有点低落");

        verify(chatPushService).pushPetExpression("u1", "sad", 0.8, 3000L);
    }

    @Test
    void pushMoodExpression_shouldDefaultBlankMoodToNeutral() {
        adapter.pushMoodExpression("u1", " ");

        verify(chatPushService).pushPetExpression("u1", "neutral", 0.8, 3000L);
    }
}
