package com.sheath.veinminer.permission;

import com.sheath.veinminer.util.Log;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
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

    public boolean hasPermission(ServerPlayer player, String permission) {
        MinecraftServer server = resolveServer(player);
        if (server != null && server.isSingleplayer()) {
            return true;
        }

        boolean isUsePermission = "veinminer.use".equalsIgnoreCase(permission);
        boolean fallback = isUsePermission || hasPermissionLevel(player, 2);

        if (luckPerms == null) {
            return fallback;
        }

        try {
            User user = cache.computeIfAbsent(player.getUUID(), this::loadUser);
            if (user == null || user.getCachedData() == null || user.getCachedData().getPermissionData() == null) {
                return fallback;
            }

            var result = user.getCachedData().getPermissionData().checkPermission(permission);
            if (result == Tristate.FALSE) {
                return false;
            }
            if (result == Tristate.UNDEFINED) {
                return fallback;
            }
            return true;
        } catch (Exception ex) {
            Log.warn("Permission check failed for {}: {}", player.getName().getString(), ex.getMessage());
            return fallback;
        }
    }

    public boolean hasPermissionLevel(CommandSourceStack source, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeBooleanInt(source, "hasPermission", level);
        if (legacy != null) {
            return legacy;
        }

        legacy = invokeBooleanInt(source, "hasPermissions", level);
        if (legacy != null) {
            return legacy;
        }

        Boolean modern = checkPermissionsApi(source, level);
        return modern != null && modern;
    }

    private boolean hasPermissionLevel(ServerPlayer player, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeBooleanInt(player, "hasPermissions", level);
        if (legacy != null) {
            return legacy;
        }

        Boolean modern = checkPermissionsApi(player, level);
        if (modern != null) {
            return modern;
        }

        try {
            return hasPermissionLevel(player.createCommandSourceStack(), level);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Boolean checkPermissionsApi(Object target, int level) {
        Object requiredPermission = permissionForLevel(level);
        if (requiredPermission == null) {
            return null;
        }

        try {
            Method permissionsMethod = target.getClass().getMethod("permissions");
            Object permissions = permissionsMethod.invoke(target);
            if (permissions == null) {
                return null;
            }
            for (Method method : permissions.getClass().getMethods()) {
                if (!"hasPermission".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                try {
                    Object result = method.invoke(permissions, requiredPermission);
                    if (result instanceof Boolean permissionResult) {
                        return permissionResult;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static Object permissionForLevel(int level) {
        String fieldName = switch (level) {
            case 1 -> "COMMANDS_MODERATOR";
            case 2 -> "COMMANDS_GAMEMASTER";
            case 3 -> "COMMANDS_ADMIN";
            default -> "COMMANDS_OWNER";
        };

        try {
            Class<?> permissionsClass = Class.forName("net.minecraft.server.permissions.Permissions");
            return permissionsClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Boolean invokeBooleanInt(Object target, String methodName, int value) {
        try {
            Method method = target.getClass().getMethod(methodName, int.class);
            Object result = method.invoke(target, value);
            return result instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private MinecraftServer resolveServer(ServerPlayer player) {
        try {
            return player.level().getServer();
        } catch (Exception ignored) {
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
