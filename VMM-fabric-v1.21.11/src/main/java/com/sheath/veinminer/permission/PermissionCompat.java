package com.sheath.veinminer.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/**
 * Supports permission checks across minor versions where named methods changed.
 */
public final class PermissionCompat {

    private PermissionCompat() {
    }

    public static boolean hasPermissionLevel(ServerCommandSource source, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeLegacyPermissionCheck(source, level);
        if (legacy != null) {
            return legacy;
        }

        Boolean predicateCheck = checkPermissionPredicate(invokeNoArg(source, "getPermissions"), level);
        return predicateCheck != null ? predicateCheck : false;
    }

    public static boolean hasPermissionLevel(ServerPlayerEntity player, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeLegacyPermissionCheck(player, level);
        if (legacy != null) {
            return legacy;
        }

        Boolean predicateCheck = checkPermissionPredicate(invokeNoArg(player, "getPermissions"), level);
        if (predicateCheck != null) {
            return predicateCheck;
        }

        try {
            return hasPermissionLevel(player.getCommandSource(), level);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Boolean invokeLegacyPermissionCheck(Object target, int level) {
        try {
            Method method = target.getClass().getMethod("hasPermissionLevel", int.class);
            Object result = method.invoke(target, level);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Boolean checkPermissionPredicate(Object permissionsPredicate, int level) {
        if (permissionsPredicate == null) {
            return null;
        }

        Object permissionToken = resolvePermissionToken(level, permissionsPredicate.getClass().getClassLoader());
        if (permissionToken == null) {
            return null;
        }

        for (Method method : permissionsPredicate.getClass().getMethods()) {
            if (!method.getName().equals("hasPermission") || method.getParameterCount() != 1) {
                continue;
            }
            try {
                Object result = method.invoke(permissionsPredicate, permissionToken);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static Object resolvePermissionToken(int level, ClassLoader classLoader) {
        for (String className : new String[]{"net.minecraft.command.DefaultPermissions", "net.minecraft.command.LeveledPermissionPredicate"}) {
            try {
                Class<?> permissionClass = Class.forName(className, false, classLoader);
                for (String fieldName : permissionFieldCandidates(level)) {
                    try {
                        return permissionClass.getField(fieldName).get(null);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static String[] permissionFieldCandidates(int level) {
        return switch (level) {
            case 1 -> new String[]{"MODERATORS", "LEVEL_1"};
            case 2 -> new String[]{"GAMEMASTERS", "LEVEL_2", "MODERATORS"};
            case 3 -> new String[]{"ADMINS", "LEVEL_3", "GAMEMASTERS"};
            default -> new String[]{"OWNERS", "LEVEL_4", "ADMINS"};
        };
    }
}
