package com.sheath.veinminer.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;

import java.lang.reflect.Method;

public final class PermissionCompat {

    private PermissionCompat() {
    }

    public static boolean hasPermissionLevel(CommandSourceStack source, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeLegacy(source, "hasPermission", level);
        if (legacy != null) {
            return legacy;
        }

        try {
            return source.permissions().hasPermission(permissionForLevel(level));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean hasPermissionLevel(ServerPlayer player, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeLegacy(player, "hasPermissions", level);
        if (legacy != null) {
            return legacy;
        }

        try {
            return player.permissions().hasPermission(permissionForLevel(level));
        } catch (Exception ignored) {
            try {
                return hasPermissionLevel(player.createCommandSourceStack(), level);
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private static Permission permissionForLevel(int level) {
        return switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
    }

    private static Boolean invokeLegacy(Object target, String methodName, int level) {
        try {
            Method method = target.getClass().getMethod(methodName, int.class);
            Object result = method.invoke(target, level);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
