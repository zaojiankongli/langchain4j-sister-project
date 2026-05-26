package com.zjkl.emotion.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmotionalStateTest {

    @Test
    void defaultConstructorCreatesNeutralState() {
        EmotionalState state = new EmotionalState();
        assertEquals(0.0, state.getPleasure());
        assertEquals(0.0, state.getArousal());
        assertEquals(0.0, state.getDominance());
    }

    @Test
    void constructorClampsValuesToRange() {
        EmotionalState state = new EmotionalState(2.0, -2.0, 1.5);
        assertEquals(1.0, state.getPleasure());
        assertEquals(-1.0, state.getArousal());
        assertEquals(1.0, state.getDominance());
    }

    @Test
    void constructorAcceptsValidValues() {
        EmotionalState state = new EmotionalState(0.5, -0.3, 0.8);
        assertEquals(0.5, state.getPleasure());
        assertEquals(-0.3, state.getArousal());
        assertEquals(0.8, state.getDominance());
    }

    @Test
    void copyReturnsIndependentInstance() {
        EmotionalState original = new EmotionalState(0.5, -0.3, 0.8);
        EmotionalState copy = original.copy();

        assertEquals(original.getPleasure(), copy.getPleasure());
        assertEquals(original.getArousal(), copy.getArousal());
        assertEquals(original.getDominance(), copy.getDominance());

        // Verify it's independent (same values, different identity)
        assertNotSame(original, copy);
    }

    @Test
    void isNeutralDetectsNeutralState() {
        EmotionalState state = new EmotionalState(0.0, 0.0, 0.0);
        assertTrue(state.isNeutral(0.1));
    }

    @Test
    void isNeutralDetectsNonNeutralState() {
        EmotionalState state = new EmotionalState(0.3, 0.0, 0.0);
        assertFalse(state.isNeutral(0.1));
        assertTrue(state.isNeutral(0.5)); // threshold large enough
    }

    @Test
    void clampedReturnsNewInstance() {
        EmotionalState state = new EmotionalState(0.5, -0.3, 0.8);
        EmotionalState clamped = state.clamped();
        assertNotSame(state, clamped);
        assertEquals(state.getPleasure(), clamped.getPleasure());
        assertEquals(state.getArousal(), clamped.getArousal());
        assertEquals(state.getDominance(), clamped.getDominance());
    }

    @Test
    void formatPadValueRoundsToThreeDecimals() {
        assertEquals(0.0, EmotionalState.formatPadValue(0.0001));
        assertEquals(0.001, EmotionalState.formatPadValue(0.001));
        assertEquals(1.0, EmotionalState.formatPadValue(1.00001));
        assertEquals(-0.333, EmotionalState.formatPadValue(-1.0 / 3.0));
    }

    @Test
    void getFormattedMethodsReturnRoundedValues() {
        EmotionalState state = new EmotionalState(1.0 / 3.0, -1.0 / 3.0, 2.0 / 3.0);
        assertEquals(0.333, state.getFormattedPleasure());
        assertEquals(-0.333, state.getFormattedArousal());
        assertEquals(0.667, state.getFormattedDominance());
    }
}
