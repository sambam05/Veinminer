package com.sheath.veinminer.state;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player cooldown windows.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isOnCooldown(ServerPlayerEntity player, int cooldownSeconds) {
        return remainingSeconds(player, cooldownSeconds) > 0;
    }

    public int remainingSeconds(ServerPlayerEntity player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        long now = Util.getMeasuringTimeMs();
        long last = cooldowns.getOrDefault(player.getUuid(), 0L);
        long remainingMs = (cooldownSeconds * 1000L) - (now - last);
        if (remainingMs <= 0L) {
            return 0;
        }
        return (int) ((remainingMs + 999L) / 1000L);
    }

    public void startCooldown(ServerPlayerEntity player) {
        cooldowns.put(player.getUuid(), Util.getMeasuringTimeMs());
    }

    public void clear(ServerPlayerEntity player) {
        cooldowns.remove(player.getUuid());
    }

    public void clearAll() {
        cooldowns.clear();
    }
}
