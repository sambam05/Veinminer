package com.sheath.veinminer.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownTrackerTest {

    @Test
    void returnsZeroForDisabledCooldown() {
        assertEquals(0, CooldownTracker.computeRemainingSeconds(10_000L, 5_000L, 0));
    }

    @Test
    void roundsUpRemainingSeconds() {
        assertEquals(2, CooldownTracker.computeRemainingSeconds(1_001L, 1_000L, 2));
        assertEquals(1, CooldownTracker.computeRemainingSeconds(1_999L, 1_000L, 1));
    }

    @Test
    void returnsZeroAfterCooldownExpires() {
        assertEquals(0, CooldownTracker.computeRemainingSeconds(5_001L, 1_000L, 4));
    }
}

