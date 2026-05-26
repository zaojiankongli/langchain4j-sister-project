package com.zjkl.emotion.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityTest {

    @Test
    void neutralPersonalityProducesNeutralPAD() {
        Personality personality = new Personality(0.0, 0.0, 0.0, 0.0, 0.0);
        EmotionalState pad = personality.toBasePAD();
        assertEquals(0.0, pad.getPleasure());
        assertEquals(0.0, pad.getArousal());
        assertEquals(0.0, pad.getDominance());
    }

    @Test
    void gentleAndShyReturnsExpectedPersonality() {
        Personality personality = Personality.gentleAndShy();
        assertEquals(0.0, personality.getOpenness());
        assertEquals(0.0, personality.getConscientiousness());
        assertEquals(-0.5, personality.getExtraversion());
        assertEquals(0.6, personality.getAgreeableness());
        assertEquals(-0.2, personality.getNeuroticism());
    }

    @Test
    void gentleAndShyBasePAD() {
        Personality personality = Personality.gentleAndShy();
        EmotionalState pad = personality.toBasePAD();

        // Expected: P = 0.59*0 + 0.19*0 + 0.21*(-0.5) + 0.15*0.6 + (-0.57)*(-0.2)
        // P = 0 + 0 + (-0.105) + 0.09 + 0.114 = 0.099
        double expectedP = 0.21 * -0.5 + 0.15 * 0.6 + (-0.57) * -0.2;

        // A = 0.25*0 + 0.60*(-0.5) + 0.17*0.6 + (-0.32)*(-0.2)
        // A = 0 + (-0.3) + 0.102 + 0.064 = -0.134
        double expectedA = 0.60 * -0.5 + 0.17 * 0.6 + (-0.32) * -0.2;

        // D = 0.40*(-0.5) + 0.20*0.6 + (-0.51)*(-0.2)
        // D = -0.2 + 0.12 + 0.102 = 0.022
        double expectedD = 0.40 * -0.5 + 0.20 * 0.6 + (-0.51) * -0.2;

        assertEquals(expectedP, pad.getPleasure(), 1e-10);
        assertEquals(expectedA, pad.getArousal(), 1e-10);
        assertEquals(expectedD, pad.getDominance(), 1e-10);
    }

    @Test
    void extremePersonalityStaysInRange() {
        Personality personality = new Personality(1.0, 1.0, 1.0, 1.0, 1.0);
        EmotionalState pad = personality.toBasePAD();
        assertTrue(pad.getPleasure() >= -1.0 && pad.getPleasure() <= 1.0);
        assertTrue(pad.getArousal() >= -1.0 && pad.getArousal() <= 1.0);
        assertTrue(pad.getDominance() >= -1.0 && pad.getDominance() <= 1.0);
    }

    @Test
    void negativeExtremePersonalityStaysInRange() {
        Personality personality = new Personality(-1.0, -1.0, -1.0, -1.0, -1.0);
        EmotionalState pad = personality.toBasePAD();
        assertTrue(pad.getPleasure() >= -1.0 && pad.getPleasure() <= 1.0);
        assertTrue(pad.getArousal() >= -1.0 && pad.getArousal() <= 1.0);
        assertTrue(pad.getDominance() >= -1.0 && pad.getDominance() <= 1.0);
    }

    @Test
    void linearMappingIsDeterministic() {
        Personality p1 = new Personality(0.3, -0.2, 0.5, 0.1, -0.4);
        Personality p2 = new Personality(0.3, -0.2, 0.5, 0.1, -0.4);
        assertEquals(p1.toBasePAD().getPleasure(), p2.toBasePAD().getPleasure());
        assertEquals(p1.toBasePAD().getArousal(), p2.toBasePAD().getArousal());
        assertEquals(p1.toBasePAD().getDominance(), p2.toBasePAD().getDominance());
    }
}
