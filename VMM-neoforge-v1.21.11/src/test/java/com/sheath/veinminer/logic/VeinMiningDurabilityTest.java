package com.sheath.veinminer.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeinMiningDurabilityTest {

    @Test
    void blockLimitRespectsDurabilityReserve() {
        assertEquals(6, VeinMiningDurability.capBlockLimit(20, true, true, 10, 4));
        assertEquals(1, VeinMiningDurability.capBlockLimit(20, true, true, 3, 4));
        assertEquals(20, VeinMiningDurability.capBlockLimit(20, false, true, 3, 4));
    }

    @Test
    void breakablePlanLimitStopsAtZeroWhenNoDurabilityLeft() {
        assertEquals(5, VeinMiningDurability.capBreakableBlocks(20, true, true, 10, 5));
        assertEquals(0, VeinMiningDurability.capBreakableBlocks(20, true, true, 4, 5));
        assertEquals(20, VeinMiningDurability.capBreakableBlocks(20, false, true, 4, 5));
    }
}
