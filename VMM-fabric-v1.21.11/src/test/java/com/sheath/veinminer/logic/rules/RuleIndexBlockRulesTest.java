package com.sheath.veinminer.logic.rules;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleIndexBlockRulesTest {

    @Test
    void matchesByIdentifier() {
        assertTrue(RuleMatcher.matches("minecraft:stone", Set.of("minecraft:stone"), Set.of(), tag -> false));
        assertFalse(RuleMatcher.matches("minecraft:dirt", Set.of("minecraft:stone"), Set.of(), tag -> false));
    }

    @Test
    void matchesByTagPredicate() {
        assertTrue(RuleMatcher.matches("minecraft:dirt", Set.of(), Set.of("#minecraft:dirt_like"), "#minecraft:dirt_like"::equals));
        assertFalse(RuleMatcher.matches("minecraft:dirt", Set.of(), Set.of("#minecraft:stone_like"), "#minecraft:dirt_like"::equals));
    }
}
