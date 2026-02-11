package com.sheath.veinminer.state;

import net.minecraft.util.Util;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player cooldown windows.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isOnCooldown(ServerPlayer player, int cooldownSeconds) {
        return remainingSeconds(player, cooldownSeconds) > 0;
    }

    public int remainingSeconds(ServerPlayer player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        long now = Util.getMillis();
        long last = cooldowns.getOrDefault(player.getUUID(), 0L);
        long remainingMs = (cooldownSeconds * 1000L) - (now - last);
        if (remainingMs <= 0L) {
            return 0;
        }
        return (int) ((remainingMs + 999L) / 1000L);
    }

    public void startCooldown(ServerPlayer player) {
        cooldowns.put(player.getUUID(), Util.getMillis());
    }

    public void clear(ServerPlayer player) {
        cooldowns.remove(player.getUUID());
    }

    public void clearAll() {
        cooldowns.clear();
    }
}

