package com.zjkl.emotion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioBufferTest {

    @Test
    void estimateDurationUsesQueuedBytesNotTotalBytes() {
        AudioBuffer buffer = new AudioBuffer(100);
        byte[] chunk = new byte[8_800];

        buffer.addAudio(chunk);
        assertEquals(100, buffer.estimateDurationMs());

        assertNotNull(buffer.pollAudio());
        assertEquals(0, buffer.estimateDurationMs());
    }

    @Test
    void byteOverflowCompletesSynthesisAndStopsFurtherCaching() {
        AudioBuffer buffer = new AudioBuffer(100);

        buffer.addAudio(new byte[2 * 1024 * 1024]);
        buffer.addAudio(new byte[1]);

        assertTrue(buffer.isSynthesisCompleted());
        int queueSizeAfterOverflow = buffer.getQueueSize();

        buffer.addAudio(new byte[1]);
        assertEquals(queueSizeAfterOverflow, buffer.getQueueSize());
        assertTrue(buffer.hasMoreAudio());
    }

    @Test
    void clearResetsOverflowAndCounters() {
        AudioBuffer buffer = new AudioBuffer(100);

        buffer.addAudio(new byte[2 * 1024 * 1024]);
        buffer.addAudio(new byte[1]);
        assertTrue(buffer.isSynthesisCompleted());

        buffer.clear();

        assertEquals(0, buffer.getQueueSize());
        assertEquals(0, buffer.estimateDurationMs());
        assertFalse(buffer.isSynthesisCompleted());

        buffer.addAudio(new byte[8_800]);
        assertEquals(1, buffer.getQueueSize());
        assertEquals(100, buffer.estimateDurationMs());
    }
}
