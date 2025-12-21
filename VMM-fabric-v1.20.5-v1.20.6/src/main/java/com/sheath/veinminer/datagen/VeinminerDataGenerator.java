package com.sheath.veinminer.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Placeholder for data generation logic. Keeping this entrypoint wired up early
 * means future content (loot tables, tags, etc.) can be produced without
 * reworking entrypoint registration.
 */
public final class VeinminerDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        fabricDataGenerator.createPack();
    }
}
