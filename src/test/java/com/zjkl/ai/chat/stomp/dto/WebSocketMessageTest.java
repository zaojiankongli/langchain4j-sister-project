package com.zjkl.ai.chat.stomp.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketMessageTest {

    @Test
    void petExpression_shouldCreateSemanticPayload() {
        WebSocketMessage message = WebSocketMessage.petExpression("happy", 0.8, 3000);

        assertEquals(MessageType.PET_EXPRESSION, message.getType());
        assertEquals("happy", message.getPayload().get("expression"));
        assertEquals(0.8, message.getPayload().get("intensity"));
        assertEquals(3000L, message.getPayload().get("durationMs"));
    }

    @Test
    void petMotion_shouldCreateSemanticPayload() {
        WebSocketMessage message = WebSocketMessage.petMotion("wave", "normal");

        assertEquals(MessageType.PET_MOTION, message.getType());
        assertEquals("wave", message.getPayload().get("motion"));
        assertEquals("normal", message.getPayload().get("priority"));
    }
}
