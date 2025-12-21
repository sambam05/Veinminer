package com.sheath.veinminer.permission;

import com.sheath.veinminer.util.Log;
import com.mojang.authlib.GameProfile;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles permission checks, optionally delegating to LuckPerms when available.
 */
public final class PermissionService {

    private LuckPerms luckPerms;
    private final Map<UUID, User> cache = new ConcurrentHashMap<>();

    /**
     * Enables or disables LuckPerms integration. When disabled the service
     * falls back to vanilla operator level checks.
     */
    public void configure(boolean enableLuckPerms) {
        if (!enableLuckPerms) {
            luckPerms = null;
            cache.clear();
            Log.info("LuckPerms integration disabled");
            return;
        }

        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPerms = LuckPermsProvider.get();
            cache.clear();
            Log.info("LuckPerms detected, permissions will use its data");
        } catch (ClassNotFoundException | IllegalStateException ex) {
            luckPerms = null;
            cache.clear();
            Log.info("LuckPerms not available, falling back to operator checks");
        }
    }

    public boolean hasPermission(ServerPlayerEntity player, String permission) {
        MinecraftServer server = resolveServer(player);
        if (server != null && server.isSingleplayer()) {
            return true;
        }

        boolean isUsePermission = "veinminer.use".equalsIgnoreCase(permission);

        if (luckPerms == null) {
            return isUsePermission || player.hasPermissionLevel(2);
        }
        try {
            User user = cache.computeIfAbsent(player.getUuid(), uuid -> loadUser(uuid));
            if (user == null || user.getCachedData() == null || user.getCachedData().getPermissionData() == null) {
                return isUsePermission || player.hasPermissionLevel(2);
            }
            var result = user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission);
            if (result == Tristate.FALSE) {
                return false;
            }
            if (result == Tristate.UNDEFINED) {
                return isUsePermission || player.hasPermissionLevel(2);
            }
            return true;
        } catch (Exception ex) {
            Log.warn("Permission check failed for {}: {}", player.getName().getString(), ex.getMessage());
            return isUsePermission || player.hasPermissionLevel(2);
        }
    }

    private MinecraftServer resolveServer(ServerPlayerEntity player) {
        try {
            // Present in 1.21.5-1.21.8 yarn: getWorld() returns ServerWorld which exposes getServer().
            return player.getWorld().getServer();
        } catch (Exception ignored) {
        }

        try {
            return player.getCommandSource().getServer();
        } catch (Exception ignored) {
        }

        // Fallbacks for any alternative mappings/fields.
        for (String getter : new String[]{"getEntityWorld", "getServerWorld", "getServer"}) {
            try {
                Object worldOrServer = player.getClass().getMethod(getter).invoke(player);
                if (worldOrServer instanceof MinecraftServer mcServer) {
                    return mcServer;
                }
                if (worldOrServer != null) {
                    Object server = worldOrServer.getClass().getMethod("getServer").invoke(worldOrServer);
                    if (server instanceof MinecraftServer mcServer) {
                        return mcServer;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

private UUID extractUuid(Object profile) {
    try {
        Object id = profile.getClass().getMethod("getId").invoke(profile);
        return id instanceof UUID ? (UUID) id : null;
    } catch (ReflectiveOperationException ignored) {
        return null;
    }
}

private String extractName(Object profile) {
    try {
        Object name = profile.getClass().getMethod("getName").invoke(profile);
        return name instanceof String ? (String) name : null;
    } catch (ReflectiveOperationException ignored) {
        return null;
    }
}

public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }

    private User loadUser(UUID uuid) {
        if (luckPerms == null) {
            return null;
        }
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) {
            return user;
        }
        try {
            return luckPerms.getUserManager().loadUser(uuid).join();
        } catch (Exception ex) {
            Log.warn("Failed to load LuckPerms data for {}: {}", uuid, ex.getMessage());
            return null;
        }
    }
}



