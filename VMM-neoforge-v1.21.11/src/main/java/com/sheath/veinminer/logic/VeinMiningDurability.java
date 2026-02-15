package com.sheath.veinminer.logic;

final class VeinMiningDurability {

    private VeinMiningDurability() {
    }

    static int capBlockLimit(int baseLimit,
                             boolean durabilityChecksEnabled,
                             boolean toolDamageable,
                             int remaining,
                             int reserve) {
        if (!durabilityChecksEnabled || !toolDamageable) {
            return baseLimit;
        }
        int breakable = Math.max(0, remaining - reserve);
        return Math.min(baseLimit, Math.max(1, breakable));
    }

    static int capBreakableBlocks(int plannedBlocks,
                                  boolean durabilityChecksEnabled,
                                  boolean toolDamageable,
                                  int remaining,
                                  int reserve) {
        if (!durabilityChecksEnabled || !toolDamageable) {
            return plannedBlocks;
        }
        int breakable = Math.max(0, remaining - reserve);
        return Math.min(plannedBlocks, breakable);
    }
}
