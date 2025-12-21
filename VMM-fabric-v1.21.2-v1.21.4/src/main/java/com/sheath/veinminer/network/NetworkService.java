package com.sheath.veinminer.network;

import com.sheath.veinminer.network.payload.HandshakeC2SPayload;
import com.sheath.veinminer.network.payload.KeyStateC2SPayload;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.state.KeyStateRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Wires Fabric networking for the mod.
 */
public final class NetworkService {

    private final KeyStateRegistry keyStates;
    private final PlayerSettingsStore playerSettings;

    public NetworkService(KeyStateRegistry keyStates, PlayerSettingsStore playerSettings) {
        this.keyStates = keyStates;
        this.playerSettings = playerSettings;
    }

    public void register() {
        PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.ID, HandshakeC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KeyStateC2SPayload.ID, KeyStateC2SPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID,
                (payload, context) -> handleHandshake(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(KeyStateC2SPayload.ID,
                (payload, context) -> handleKeyState(context.player(), payload.pressed()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                keyStates.unregister(handler.player));
    }

    private void handleHandshake(ServerPlayerEntity player) {
        keyStates.registerClient(player);
        player.getCommandSource().getServer().getCommandManager().sendCommandTree(player);
    }

    private void handleKeyState(ServerPlayerEntity player, boolean pressed) {
        boolean wasPressed = keyStates.setKeyState(player, pressed);
        if (!playerSettings.useKeybind(player)) {
            return;
        }
        if (playerSettings.keyToggleMode(player)) {
            if (pressed && !wasPressed) {
                playerSettings.flipKeyToggleState(player);
            }
        }
    }
}






