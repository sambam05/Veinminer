package com.sheath.veinminer.state;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players have the client mod installed and their current key
 * states.
 */
public final class KeyStateRegistry {

    private final Set<UUID> modPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> keyStates = new ConcurrentHashMap<>();

    public void registerClient(ServerPlayerEntity player) {
        modPlayers.add(player.getUuid());
    }

    public void unregister(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        modPlayers.remove(uuid);
        keyStates.remove(uuid);
    }

    public boolean setKeyState(ServerPlayerEntity player, boolean pressed) {
        UUID uuid = player.getUuid();
        Boolean previous = keyStates.put(uuid, pressed);
        return previous != null && previous;
    }

    public boolean hasClient(ServerPlayerEntity player) {
        return modPlayers.contains(player.getUuid());
    }

    public boolean isKeyPressed(ServerPlayerEntity player) {
        return keyStates.getOrDefault(player.getUuid(), false);
    }

    public void clearKeyState(ServerPlayerEntity player) {
        keyStates.remove(player.getUuid());
    }
}
