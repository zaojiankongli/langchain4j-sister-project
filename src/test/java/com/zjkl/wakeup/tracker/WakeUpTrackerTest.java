package com.zjkl.wakeup.tracker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WakeUpTrackerTest {

    @Test
    void maybeSwap_shouldKeepOriginalBestIndexAndTrackActualSentIndex() {
        WakeUpTracker tracker = new WakeUpTracker(null);

        WakeUpTracker.SwapResult result = tracker.maybeSwap(
                List.of("A", "B", "C"),
                new int[]{9, 7, 5},
                0,
                0.0,
                1
        );

        assertEquals(0, result.getOriginalBestIndex());
        assertEquals(1, result.getActualSentIndex());
        assertEquals("B", result.getMessage());
        assertTrue(result.isSwapped());
    }

    @Test
    void maybeSwap_shouldUseBestIndexAsActualSentIndexWhenNotSwapped() {
        WakeUpTracker tracker = new WakeUpTracker(null);

        WakeUpTracker.SwapResult result = tracker.maybeSwap(
                List.of("A", "B", "C"),
                new int[]{9, 7, 5},
                0,
                0.9,
                null
        );

        assertEquals(0, result.getOriginalBestIndex());
        assertEquals(0, result.getActualSentIndex());
        assertEquals("A", result.getMessage());
        assertFalse(result.isSwapped());
    }
}
