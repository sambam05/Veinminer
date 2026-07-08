package com.sheath.veinminer.player;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class PlayerSettingsNbtCompat {
    private static final Method GET_BOOLEAN = resolve("getBoolean");
    private static final Method GET_INT = resolve("getInt");
    private static final Method GET_COMPOUND = resolve("getCompound");
    private static final Codec<?> NBT_CODEC = resolveNbtCodec();

    private PlayerSettingsNbtCompat() {
    }

    public static boolean getBoolean(NbtCompound nbt, String key, boolean defaultValue) {
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

    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
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

    public static NbtCompound getCompound(NbtCompound nbt, String key) {
        Object value = invoke(GET_COMPOUND, nbt, key);
        if (value instanceof NbtCompound compound) {
            return compound;
        }
        if (value instanceof Optional<?> optional) {
            Object inner = optional.orElse(null);
            if (inner instanceof NbtCompound compound) {
                return compound;
            }
        }
        return new NbtCompound();
    }

    public static NbtCompound readCompoundFromReadView(Object readView, String key) {
        if (readView == null || NBT_CODEC == null) {
            return new NbtCompound();
        }
        try {
            Method read = readView.getClass().getMethod("read", String.class, Codec.class);
            Object value = read.invoke(readView, key, NBT_CODEC);
            if (value instanceof Optional<?> optional) {
                Object inner = optional.orElse(null);
                if (inner instanceof NbtCompound compound) {
                    return compound;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return new NbtCompound();
    }

    public static void writeCompoundToWriteView(Object writeView, String key, NbtCompound value) {
        if (writeView == null || NBT_CODEC == null) {
            return;
        }
        try {
            Method put = writeView.getClass().getMethod("put", String.class, Codec.class, Object.class);
            put.invoke(writeView, key, NBT_CODEC, value.copy());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method resolve(String methodName) {
        try {
            return NbtCompound.class.getMethod(methodName, String.class);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Object invoke(Method method, NbtCompound nbt, String key) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(nbt, key);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Codec<?> resolveNbtCodec() {
        try {
            Field field = NbtCompound.class.getField("CODEC");
            Object value = field.get(null);
            if (value instanceof Codec<?> codec) {
                return codec;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
