package com.sheath.veinminer.command;

import com.sheath.veinminer.core.Bootstrap;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SetupWelcomePrompt {

    private SetupWelcomePrompt() {
    }

    public static void maybeSend(Bootstrap bootstrap, ServerPlayerEntity player) {
        // Setup flow is intentionally disabled in the streamlined default experience.
    }
}
