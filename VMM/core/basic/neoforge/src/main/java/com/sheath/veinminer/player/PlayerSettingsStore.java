package com.sheath.veinminer.player;

import com.sheath.veinminer.concurrent.TaskExecutor;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.util.Log;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists per-player settings directly in each player's NBT data.
 */
public final class PlayerSettingsStore {

    public static final int MAX_PARTICLE_DURATION_TICKS = 20 * 60;

    private final Path legacyDataFile = FMLPaths.CONFIGDIR.get()
            .resolve(ModConstants.CONFIG_DIRECTORY)
            .resolve("Players")
            .resolve("PlayerData.json");

    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    @SuppressWarnings("unused")
    private final TaskExecutor executor;

    public PlayerSettingsStore(TaskExecutor executor) {
        this.executor = executor;
    }

    public void load() {
        settings.clear();
        deleteLegacyJsonFile();
    }

    public CompletableFuture<Void> saveAsync() {
        // Data is already stored directly in player NBT and saved by the game.
        return CompletableFuture.completedFuture(null);
    }

    public void saveBlocking() {
        // Data is already stored directly in player NBT and saved by the game.
    }

    public boolean isVeinminerEnabled(ServerPlayer player) {
        return settingsFor(player).veinminerEnabled;
    }

    public void setVeinminerEnabled(ServerPlayer player, boolean enabled) {
        PlayerSettings settings = settingsFor(player);
        settings.veinminerEnabled = enabled;
        writeToNbt(player, settings);
    }

    public boolean isParticlesEnabled(ServerPlayer player) {
        return settingsFor(player).particlesEnabled;
    }

    public void setParticlesEnabled(ServerPlayer player, boolean enabled) {
        PlayerSettings settings = settingsFor(player);
        settings.particlesEnabled = enabled;
        writeToNbt(player, settings);
    }

    public int particleDurationTicks(ServerPlayer player) {
        return settingsFor(player).particleDurationTicks;
    }

    public void setParticleDurationTicks(ServerPlayer player, int ticks) {
        PlayerSettings settings = settingsFor(player);
        settings.particleDurationTicks = clampDuration(ticks);
        writeToNbt(player, settings);
    }

    public int particleRed(ServerPlayer player) {
        return settingsFor(player).particleRed;
    }

    public int particleGreen(ServerPlayer player) {
        return settingsFor(player).particleGreen;
    }

    public int particleBlue(ServerPlayer player) {
        return settingsFor(player).particleBlue;
    }

    public void setParticleColor(ServerPlayer player, int red, int green, int blue) {
        PlayerSettings settings = settingsFor(player);
        settings.particleRed = clampColor(red);
        settings.particleGreen = clampColor(green);
        settings.particleBlue = clampColor(blue);
        writeToNbt(player, settings);
    }

    public boolean isMessageEnabled(ServerPlayer player, MessageType type) {
        return settingsFor(player).messageEnabled(type);
    }

    public void setMessageEnabled(ServerPlayer player, MessageType type, boolean enabled) {
        PlayerSettings settings = settingsFor(player);
        settings.setMessage(type, enabled);
        writeToNbt(player, settings);
    }

    public boolean useKeybind(ServerPlayer player) {
        return settingsFor(player).useKeybind;
    }

    public void setUseKeybind(ServerPlayer player, boolean useKeybind) {
        PlayerSettings settings = settingsFor(player);
        settings.useKeybind = useKeybind;
        writeToNbt(player, settings);
    }

    public boolean keyToggleMode(ServerPlayer player) {
        return settingsFor(player).keyToggleMode;
    }

    public void setKeyToggleMode(ServerPlayer player, boolean toggle) {
        PlayerSettings settings = settingsFor(player);
        settings.keyToggleMode = toggle;
        writeToNbt(player, settings);
    }

    public boolean isKeyToggleActive(ServerPlayer player) {
        return settingsFor(player).keyToggleActive;
    }

    public void setKeyToggleState(ServerPlayer player, boolean active) {
        PlayerSettings settings = settingsFor(player);
        settings.keyToggleActive = active;
        writeToNbt(player, settings);
    }

    public boolean flipKeyToggleState(ServerPlayer player) {
        PlayerSettings settings = settingsFor(player);
        settings.keyToggleActive = !settings.keyToggleActive;
        writeToNbt(player, settings);
        return settings.keyToggleActive;
    }

    public void resetKeyToggleState(ServerPlayer player) {
        PlayerSettings settings = settingsFor(player);
        settings.keyToggleActive = false;
        writeToNbt(player, settings);
    }

    public boolean crouchToggleMode(ServerPlayer player) {
        return settingsFor(player).crouchToggleMode;
    }

    public void setCrouchToggleMode(ServerPlayer player, boolean toggle) {
        PlayerSettings settings = settingsFor(player);
        settings.crouchToggleMode = toggle;
        writeToNbt(player, settings);
    }

    public boolean isCrouchToggleActive(ServerPlayer player) {
        return settingsFor(player).crouchToggleActive;
    }

    public void setCrouchToggleState(ServerPlayer player, boolean active) {
        PlayerSettings settings = settingsFor(player);
        settings.crouchToggleActive = active;
        writeToNbt(player, settings);
    }

    public boolean updateCrouchToggleState(ServerPlayer player, boolean sneaking) {
        PlayerSettings settings = settingsFor(player);
        if (!settings.crouchToggleMode) {
            settings.lastCrouchInput = sneaking;
            return sneaking;
        }
        if (sneaking && !settings.lastCrouchInput) {
            settings.crouchToggleActive = !settings.crouchToggleActive;
            writeToNbt(player, settings);
        }
        settings.lastCrouchInput = sneaking;
        return settings.crouchToggleActive;
    }

    public boolean lastCrouchInput(ServerPlayer player) {
        return settingsFor(player).lastCrouchInput;
    }

    public void setLastCrouchInput(ServerPlayer player, boolean value) {
        settingsFor(player).lastCrouchInput = value;
    }

    public void resetCrouchToggleState(ServerPlayer player) {
        PlayerSettings settings = settingsFor(player);
        settings.crouchToggleActive = false;
        settings.lastCrouchInput = false;
        writeToNbt(player, settings);
    }

    public void drop(ServerPlayer player) {
        settings.remove(player.getUUID());
    }

    public void saveAndDrop(ServerPlayer player) {
        PlayerSettings value = settings.get(player.getUUID());
        if (value != null) {
            writeToNbt(player, value);
        }
        drop(player);
    }

    private PlayerSettings settingsFor(ServerPlayer player) {
        return settings.computeIfAbsent(player.getUUID(), __ -> readFromNbt(player));
    }

    private PlayerSettings readFromNbt(ServerPlayer player) {
        CompoundTag root = rootData(player);
        PlayerSettings value = new PlayerSettings();

        value.veinminerEnabled = readBoolean(root, PlayerSettingsNbtKeys.VEINMINER_ENABLED, true);
        value.particlesEnabled = readBoolean(root, PlayerSettingsNbtKeys.PARTICLES_ENABLED, true);

        CompoundTag particles = getCompound(root, PlayerSettingsNbtKeys.PARTICLES);
        value.particleDurationTicks = clampDuration(readInt(particles, PlayerSettingsNbtKeys.PARTICLE_DURATION_TICKS, value.particleDurationTicks));
        value.particleRed = clampColor(readInt(particles, PlayerSettingsNbtKeys.PARTICLE_RED, value.particleRed));
        value.particleGreen = clampColor(readInt(particles, PlayerSettingsNbtKeys.PARTICLE_GREEN, value.particleGreen));
        value.particleBlue = clampColor(readInt(particles, PlayerSettingsNbtKeys.PARTICLE_BLUE, value.particleBlue));

        CompoundTag messages = getCompound(root, PlayerSettingsNbtKeys.MESSAGES);
        for (MessageType type : MessageType.values()) {
            value.setMessage(type, readBoolean(messages, type.id(), true));
        }

        CompoundTag input = getCompound(root, PlayerSettingsNbtKeys.INPUT);
        value.useKeybind = readBoolean(input, PlayerSettingsNbtKeys.USE_KEYBIND, false);
        value.keyToggleMode = readBoolean(input, PlayerSettingsNbtKeys.KEY_TOGGLE, false);
        value.keyToggleActive = readBoolean(input, PlayerSettingsNbtKeys.KEY_TOGGLE_STATE, false);
        value.crouchToggleMode = readBoolean(input, PlayerSettingsNbtKeys.CROUCH_TOGGLE, false);
        value.crouchToggleActive = readBoolean(input, PlayerSettingsNbtKeys.CROUCH_TOGGLE_STATE, false);
        return value;
    }

    private void writeToNbt(ServerPlayer player, PlayerSettings value) {
        CompoundTag root = rootData(player);
        root.putBoolean(PlayerSettingsNbtKeys.VEINMINER_ENABLED, value.veinminerEnabled);
        root.putBoolean(PlayerSettingsNbtKeys.PARTICLES_ENABLED, value.particlesEnabled);

        CompoundTag particles = new CompoundTag();
        particles.putInt(PlayerSettingsNbtKeys.PARTICLE_DURATION_TICKS, value.particleDurationTicks);
        particles.putInt(PlayerSettingsNbtKeys.PARTICLE_RED, value.particleRed);
        particles.putInt(PlayerSettingsNbtKeys.PARTICLE_GREEN, value.particleGreen);
        particles.putInt(PlayerSettingsNbtKeys.PARTICLE_BLUE, value.particleBlue);
        root.put(PlayerSettingsNbtKeys.PARTICLES, particles);

        CompoundTag messages = new CompoundTag();
        for (MessageType type : MessageType.values()) {
            messages.putBoolean(type.id(), value.messageEnabled(type));
        }
        root.put(PlayerSettingsNbtKeys.MESSAGES, messages);

        CompoundTag input = new CompoundTag();
        input.putBoolean(PlayerSettingsNbtKeys.USE_KEYBIND, value.useKeybind);
        input.putBoolean(PlayerSettingsNbtKeys.KEY_TOGGLE, value.keyToggleMode);
        input.putBoolean(PlayerSettingsNbtKeys.KEY_TOGGLE_STATE, value.keyToggleActive);
        input.putBoolean(PlayerSettingsNbtKeys.CROUCH_TOGGLE, value.crouchToggleMode);
        input.putBoolean(PlayerSettingsNbtKeys.CROUCH_TOGGLE_STATE, value.crouchToggleActive);
        root.put(PlayerSettingsNbtKeys.INPUT, input);
    }

    private CompoundTag rootData(ServerPlayer player) {
        return ((PlayerSettingsDataHolder) player).veinminer$getPlayerData();
    }

    private static boolean readBoolean(CompoundTag nbt, String key, boolean defaultValue) {
        return PlayerSettingsNbtCompat.getBoolean(nbt, key, defaultValue);
    }

    private static int readInt(CompoundTag nbt, String key, int defaultValue) {
        return PlayerSettingsNbtCompat.getInt(nbt, key, defaultValue);
    }

    private static CompoundTag getCompound(CompoundTag nbt, String key) {
        return PlayerSettingsNbtCompat.getCompound(nbt, key);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clampDuration(int ticks) {
        return Math.max(1, Math.min(MAX_PARTICLE_DURATION_TICKS, ticks));
    }

    private void deleteLegacyJsonFile() {
        try {
            if (Files.deleteIfExists(legacyDataFile)) {
                Log.info("Deleted deprecated player data file '{}'", legacyDataFile);
            }
        } catch (IOException ex) {
            Log.warn("Failed to delete deprecated player data file '{}': {}", legacyDataFile, ex.getMessage());
        }
    }

    private static final class PlayerSettings {
        boolean veinminerEnabled = true;
        boolean particlesEnabled = true;
        int particleDurationTicks = 60;
        int particleRed = 255;
        int particleGreen = 0;
        int particleBlue = 0;
        boolean useKeybind = false;
        boolean keyToggleMode = false;
        boolean keyToggleActive = false;
        boolean crouchToggleMode = false;
        boolean crouchToggleActive = false;
        boolean lastCrouchInput = false;
        final EnumMap<MessageType, Boolean> messages = new EnumMap<>(MessageType.class);

        boolean messageEnabled(MessageType type) {
            return messages.getOrDefault(type, Boolean.TRUE);
        }

        void setMessage(MessageType type, boolean enabled) {
            messages.put(type, enabled);
        }
    }

    public enum MessageType {
        PERMISSION("permission", "Permission"),
        DISABLED("disabled", "Disabled"),
        COOLDOWN("cooldown", "Cooldown"),
        DURABILITY("durability", "Durability");

        private final String id;
        private final String displayName;

        MessageType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }
    }
}
