package com.sheath.veinminer.state;

import net.minecraft.server.level.ServerPlayer;

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

    public void registerClient(ServerPlayer player) {
        modPlayers.add(player.getUUID());
    }

    public void unregister(ServerPlayer player) {
        UUID uuid = player.getUUID();
        modPlayers.remove(uuid);
        keyStates.remove(uuid);
    }

    public boolean setKeyState(ServerPlayer player, boolean pressed) {
        UUID uuid = player.getUUID();
        Boolean previous = keyStates.put(uuid, pressed);
        return previous != null && previous;
    }

    public boolean hasClient(ServerPlayer player) {
        return modPlayers.contains(player.getUUID());
    }

    public boolean isKeyPressed(ServerPlayer player) {
        return keyStates.getOrDefault(player.getUUID(), false);
    }

    public void clearKeyState(ServerPlayer player) {
        keyStates.remove(player.getUUID());
    }
}
