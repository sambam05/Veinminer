package com.sheath.veinminer.util;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

public final class RegistryLookup {

    private RegistryLookup() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Registry<T> requireRegistry(DynamicRegistryManager manager,
                                                  RegistryKey<Registry<T>> key) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(key, "key");
        try {
            Method method = DynamicRegistryManager.class.getMethod("getOrThrow", RegistryKey.class);
            return (Registry<T>) method.invoke(manager, key);
        } catch (NoSuchMethodException ignored) {
            return resolveWithoutGetOrThrow(manager, key);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to resolve registry: " + key.getValue(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> resolveWithoutGetOrThrow(DynamicRegistryManager manager,
                                                            RegistryKey<Registry<T>> key) {
        try {
            Method getOptional = DynamicRegistryManager.class.getMethod("getOptional", RegistryKey.class);
            Optional<Registry<T>> optional = (Optional<Registry<T>>) getOptional.invoke(manager, key);
            return optional.orElseThrow(() -> missingRegistry(key));
        } catch (NoSuchMethodException ignored) {
            return resolveUsingGet(manager, key);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to resolve registry: " + key.getValue(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> resolveUsingGet(DynamicRegistryManager manager,
                                                   RegistryKey<Registry<T>> key) {
        try {
            Method get = DynamicRegistryManager.class.getMethod("get", RegistryKey.class);
            Object result = get.invoke(manager, key);
            if (result instanceof Optional<?> optional) {
                return (Registry<T>) optional.orElseThrow(() -> missingRegistry(key));
            }
            return (Registry<T>) Objects.requireNonNull(result, () -> missingRegistry(key).getMessage());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to resolve registry: " + key.getValue(), ex);
        }
    }

    private static IllegalStateException missingRegistry(RegistryKey<?> key) {
        return new IllegalStateException("Missing registry: " + key.getValue());
    }
}
