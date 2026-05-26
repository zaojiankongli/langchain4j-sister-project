package com.zjkl.emotion.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaEmotionTest {

    @Test
    void positiveFullIntensity() {
        DeltaEmotion delta = DeltaEmotion.positive(1.0);
        assertEquals(0.3, delta.getDeltaP());
        assertEquals(0.2, delta.getDeltaA());
        assertEquals(0.1, delta.getDeltaD());
    }

    @Test
    void positiveHalfIntensity() {
        DeltaEmotion delta = DeltaEmotion.positive(0.5);
        assertEquals(0.15, delta.getDeltaP());
        assertEquals(0.1, delta.getDeltaA());
        assertEquals(0.05, delta.getDeltaD());
    }

    @Test
    void negativeFullIntensity() {
        DeltaEmotion delta = DeltaEmotion.negative(1.0);
        assertEquals(-0.3, delta.getDeltaP());
        assertEquals(-0.1, delta.getDeltaA());
        assertEquals(-0.2, delta.getDeltaD());
    }

    @Test
    void negativeHalfIntensity() {
        DeltaEmotion delta = DeltaEmotion.negative(0.5);
        assertEquals(-0.15, delta.getDeltaP());
        assertEquals(-0.05, delta.getDeltaA());
        assertEquals(-0.1, delta.getDeltaD());
    }

    @Test
    void shyFullIntensity() {
        DeltaEmotion delta = DeltaEmotion.shy(1.0);
        assertEquals(0.1, delta.getDeltaP());
        assertEquals(0.4, delta.getDeltaA());
        assertEquals(-0.3, delta.getDeltaD());
    }

    @Test
    void zeroIntensityReturnsZeroDelta() {
        DeltaEmotion delta = DeltaEmotion.positive(0.0);
        assertEquals(0.0, delta.getDeltaP());
        assertEquals(0.0, delta.getDeltaA());
        assertEquals(0.0, delta.getDeltaD());
    }

    @Test
    void clampBoundsValues() {
        DeltaEmotion delta = new DeltaEmotion(2.0, -2.0, 1.5);
        delta.clamp();
        assertEquals(1.0, delta.getDeltaP());
        assertEquals(-1.0, delta.getDeltaA());
        assertEquals(1.0, delta.getDeltaD());
    }

    @Test
    void clampHandlesNullValues() {
        DeltaEmotion delta = new DeltaEmotion(null, null, null);
        delta.clamp();
        assertEquals(0.0, delta.getDeltaP());
        assertEquals(0.0, delta.getDeltaA());
        assertEquals(0.0, delta.getDeltaD());
    }

    @Test
    void noArgsConstructorCreatesNullDeltas() {
        DeltaEmotion delta = new DeltaEmotion();
        assertNull(delta.getDeltaP());
        assertNull(delta.getDeltaA());
        assertNull(delta.getDeltaD());
    }
}
