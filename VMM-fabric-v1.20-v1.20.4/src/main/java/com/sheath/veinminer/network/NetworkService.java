package com.sheath.veinminer.network;

import com.sheath.veinminer.network.payload.HandshakeC2SPayload;
import com.sheath.veinminer.network.payload.KeyStateC2SPayload;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.state.KeyStateRegistry;
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
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID,
                (server, player, handler, buf, responseSender) -> handleHandshake(player));

        ServerPlayNetworking.registerGlobalReceiver(KeyStateC2SPayload.ID,
                (server, player, handler, buf, responseSender) -> handleKeyState(player, KeyStateC2SPayload.read(buf)));

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






