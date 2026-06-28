package com.sheath.veinminer.state;

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
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUUID(), 0L);
        return computeRemainingSeconds(now, last, cooldownSeconds);
    }

    public void startCooldown(ServerPlayer player) {
        cooldowns.put(player.getUUID(), System.currentTimeMillis());
    }

    public void clear(ServerPlayer player) {
        cooldowns.remove(player.getUUID());
    }

    public void clearAll() {
        cooldowns.clear();
    }

    static int computeRemainingSeconds(long nowMs, long lastMs, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        long remainingMs = (cooldownSeconds * 1000L) - (nowMs - lastMs);
        if (remainingMs <= 0L) {
            return 0;
        }
        return (int) ((remainingMs + 999L) / 1000L);
    }
}
