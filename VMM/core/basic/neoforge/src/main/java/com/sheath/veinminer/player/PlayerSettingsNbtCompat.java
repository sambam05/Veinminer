package com.sheath.veinminer.player;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.Optional;

public final class PlayerSettingsNbtCompat {
    private static final Method GET_BOOLEAN = resolve("getBoolean");
    private static final Method GET_INT = resolve("getInt");
    private static final Method GET_COMPOUND = resolve("getCompound");

    private PlayerSettingsNbtCompat() {
    }

    public static boolean getBoolean(CompoundTag nbt, String key, boolean defaultValue) {
        Object value = invoke(GET_BOOLEAN, nbt, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Optional<?> optional) {
            Object inner = optional.orElse(null);
            if (inner instanceof Boolean bool) {
                return bool;
            }
        }
        return defaultValue;
    }

    public static int getInt(CompoundTag nbt, String key, int defaultValue) {
        Object value = invoke(GET_INT, nbt, key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Optional<?> optional) {
            Object inner = optional.orElse(null);
            if (inner instanceof Integer integer) {
                return integer;
            }
            if (inner instanceof Number number) {
                return number.intValue();
            }
        }
        return defaultValue;
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key) {
        Object value = invoke(GET_COMPOUND, nbt, key);
        if (value instanceof CompoundTag compound) {
            return compound;
        }
        if (value instanceof Optional<?> optional) {
            Object inner = optional.orElse(null);
            if (inner instanceof CompoundTag compound) {
                return compound;
            }
        }
        return new CompoundTag();
    }

    private static Method resolve(String methodName) {
        try {
            return CompoundTag.class.getMethod(methodName, String.class);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Object invoke(Method method, CompoundTag nbt, String key) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(nbt, key);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
