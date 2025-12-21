package com.sheath.veinminer;

import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.util.Log;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint for the Veinminer mod.
 *
 * <p>This class keeps the top-level surface area intentionally small and
 * delegates all heavy lifting to {@link Bootstrap}. Doing so makes it easier to
 * reason about shared state and extend behaviour in later versions without
 * proliferating static singletons across the codebase.</p>
 */
public final class Veinminer implements ModInitializer {

    public static final Bootstrap BOOTSTRAP = new Bootstrap();

    @Override
    public void onInitialize() {
        Log.info("Starting {} (id={})", ModConstants.MOD_NAME, ModConstants.MOD_ID);
        BOOTSTRAP.onCommonSetup();
        Log.info("{} initialisation completed", ModConstants.MOD_NAME);
    }
}
