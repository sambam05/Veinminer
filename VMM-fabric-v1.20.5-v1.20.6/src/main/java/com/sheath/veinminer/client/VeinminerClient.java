package com.sheath.veinminer.client;

import com.sheath.veinminer.Veinminer;
import com.sheath.veinminer.network.payload.HandshakeC2SPayload;
import com.sheath.veinminer.network.payload.KeyStateC2SPayload;
import com.sheath.veinminer.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client entrypoint. Delegates to the shared {@link Veinminer#BOOTSTRAP}
 * so that all wiring happens in a single place.
 */
@Environment(EnvType.CLIENT)
public final class VeinminerClient implements ClientModInitializer {

    private static final String KEY_CATEGORY = "category.veinminer";

    private static KeyBinding activateKey;
    private static boolean lastState = false;

    @Override
    public void onInitializeClient() {
        Log.info("Initialising client bootstrap");
        Veinminer.BOOTSTRAP.onClientSetup();

        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.veinminer.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KEY_CATEGORY
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientPlayNetworking.send(new HandshakeC2SPayload()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean pressed = activateKey.isPressed();
            if (pressed != lastState) {
                lastState = pressed;
                ClientPlayNetworking.send(new KeyStateC2SPayload(pressed));
            }
        });
    }
}
