package com.sheath.veinminer.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sheath.veinminer.concurrent.TaskExecutor;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persists per-player settings such as custom toggles and message preferences.
 */
public final class PlayerSettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(ModConstants.CONFIG_DIRECTORY)
            .resolve("Players")
            .resolve("PlayerData.json");

    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final TaskExecutor executor;
    private final Lock writeLock = new ReentrantLock();

    public PlayerSettingsStore(TaskExecutor executor) {
        this.executor = executor;
    }

    public void load() {
        ensureParentDirectory();
        if (!Files.exists(dataFile)) {
            saveBlocking();
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                Log.warn("Player data file did not contain a JSON object, resetting");
                saveBlocking();
                return;
            }
            JsonObject root = element.getAsJsonObject();
            parseVeinminerStates(root.getAsJsonObject("veinminer"));
            parseParticleStates(root.getAsJsonObject("particles"));
            parseMessageStates(root.getAsJsonObject("messages"));
            parseInputStates(root.getAsJsonObject("input"));
            Log.info("Loaded player preference data for {} players", settings.size());
        } catch (IOException ex) {
            Log.error("Failed to read player data file", ex);
        }
    }

    public CompletableFuture<Void> saveAsync() {
        return executor.submitAsync(() -> {
            writeLock.lock();
            try {
                ensureParentDirectory();
                try (Writer writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(buildSerializableJson(), writer);
                }
            } catch (IOException ex) {
                Log.error("Failed to save player data", ex);
            } finally {
                writeLock.unlock();
            }
            return null;
        });
    }

    public void saveBlocking() {
        writeLock.lock();
        try {
            ensureParentDirectory();
            try (Writer writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
                GSON.toJson(buildSerializableJson(), writer);
            }
        } catch (IOException ex) {
            Log.error("Failed to save player data", ex);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isVeinminerEnabled(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).veinminerEnabled;
    }

    public void setVeinminerEnabled(ServerPlayerEntity player, boolean enabled) {
        settingsFor(player.getUuid()).veinminerEnabled = enabled;
    }

    public boolean isParticlesEnabled(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).particlesEnabled;
    }

    public void setParticlesEnabled(ServerPlayerEntity player, boolean enabled) {
        settingsFor(player.getUuid()).particlesEnabled = enabled;
    }

    public boolean isMessageEnabled(ServerPlayerEntity player, MessageType type) {
        return settingsFor(player.getUuid()).messageEnabled(type);
    }

    public void setMessageEnabled(ServerPlayerEntity player, MessageType type, boolean enabled) {
        settingsFor(player.getUuid()).setMessage(type, enabled);
    }

    public boolean useKeybind(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).useKeybind;
    }

    public void setUseKeybind(ServerPlayerEntity player, boolean useKeybind) {
        settingsFor(player.getUuid()).useKeybind = useKeybind;
    }

    public boolean keyToggleMode(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).keyToggleMode;
    }

    public void setKeyToggleMode(ServerPlayerEntity player, boolean toggle) {
        settingsFor(player.getUuid()).keyToggleMode = toggle;
    }

    public boolean isKeyToggleActive(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).keyToggleActive;
    }

    public void setKeyToggleState(ServerPlayerEntity player, boolean active) {
        settingsFor(player.getUuid()).keyToggleActive = active;
    }

    public boolean flipKeyToggleState(ServerPlayerEntity player) {
        PlayerSettings settings = settingsFor(player.getUuid());
        settings.keyToggleActive = !settings.keyToggleActive;
        return settings.keyToggleActive;
    }

    public void resetKeyToggleState(ServerPlayerEntity player) {
        PlayerSettings settings = settingsFor(player.getUuid());
        settings.keyToggleActive = false;
    }

    public boolean crouchToggleMode(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).crouchToggleMode;
    }

    public void setCrouchToggleMode(ServerPlayerEntity player, boolean toggle) {
        settingsFor(player.getUuid()).crouchToggleMode = toggle;
    }

    public boolean isCrouchToggleActive(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).crouchToggleActive;
    }

    public void setCrouchToggleState(ServerPlayerEntity player, boolean active) {
        settingsFor(player.getUuid()).crouchToggleActive = active;
    }

    public boolean updateCrouchToggleState(ServerPlayerEntity player, boolean sneaking) {
        PlayerSettings settings = settingsFor(player.getUuid());
        if (!settings.crouchToggleMode) {
            settings.lastCrouchInput = sneaking;
            return sneaking;
        }
        if (sneaking && !settings.lastCrouchInput) {
            settings.crouchToggleActive = !settings.crouchToggleActive;
        }
        settings.lastCrouchInput = sneaking;
        return settings.crouchToggleActive;
    }

    public boolean lastCrouchInput(ServerPlayerEntity player) {
        return settingsFor(player.getUuid()).lastCrouchInput;
    }

    public void setLastCrouchInput(ServerPlayerEntity player, boolean value) {
        settingsFor(player.getUuid()).lastCrouchInput = value;
    }

    public void resetCrouchToggleState(ServerPlayerEntity player) {
        PlayerSettings settings = settingsFor(player.getUuid());
        settings.crouchToggleActive = false;
        settings.lastCrouchInput = false;
    }

    public void drop(ServerPlayerEntity player) {
        settings.remove(player.getUuid());
    }

    public void saveAndDrop(ServerPlayerEntity player) {
        saveBlocking();
        drop(player);
    }

    private void parseVeinminerStates(JsonObject object) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null) continue;
            if (!entry.getValue().isJsonPrimitive()) continue;
            settingsFor(uuid).veinminerEnabled = entry.getValue().getAsBoolean();
        }
    }

    private void parseParticleStates(JsonObject object) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null) continue;
            if (!entry.getValue().isJsonPrimitive()) continue;
            settingsFor(uuid).particlesEnabled = entry.getValue().getAsBoolean();
        }
    }

    private void parseMessageStates(JsonObject object) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null) continue;
            if (!entry.getValue().isJsonObject()) continue;
            PlayerSettings playerSettings = settingsFor(uuid);
            JsonObject messageObject = entry.getValue().getAsJsonObject();
            for (MessageType type : MessageType.values()) {
                JsonElement val = messageObject.get(type.id());
                if (val != null && val.isJsonPrimitive()) {
                    playerSettings.setMessage(type, val.getAsBoolean());
                }
            }
        }
    }

    private void parseInputStates(JsonObject object) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null) continue;
            if (!entry.getValue().isJsonObject()) continue;
            PlayerSettings settings = settingsFor(uuid);
            JsonObject input = entry.getValue().getAsJsonObject();
            JsonElement useKey = input.get("useKeybind");
            if (useKey != null && useKey.isJsonPrimitive()) {
                settings.useKeybind = useKey.getAsBoolean();
            }
            JsonElement keyToggle = input.get("keyToggle");
            if (keyToggle != null && keyToggle.isJsonPrimitive()) {
                settings.keyToggleMode = keyToggle.getAsBoolean();
            }
            JsonElement keyState = input.get("keyToggleState");
            if (keyState != null && keyState.isJsonPrimitive()) {
                settings.keyToggleActive = keyState.getAsBoolean();
            }
            JsonElement crouchToggle = input.get("crouchToggle");
            if (crouchToggle != null && crouchToggle.isJsonPrimitive()) {
                settings.crouchToggleMode = crouchToggle.getAsBoolean();
            }
            JsonElement crouchState = input.get("crouchToggleState");
            if (crouchState != null && crouchState.isJsonPrimitive()) {
                settings.crouchToggleActive = crouchState.getAsBoolean();
            }
        }
    }

    private PlayerSettings settingsFor(UUID uuid) {
        return settings.computeIfAbsent(uuid, __ -> new PlayerSettings());
    }

    private JsonObject buildSerializableJson() {
        JsonObject root = new JsonObject();

        JsonObject veinminerObj = new JsonObject();
        JsonObject particlesObj = new JsonObject();
        JsonObject messagesObj = new JsonObject();
        JsonObject inputObj = new JsonObject();

        for (Map.Entry<UUID, PlayerSettings> entry : settings.entrySet()) {
            String key = entry.getKey().toString();
            PlayerSettings value = entry.getValue();
            veinminerObj.addProperty(key, value.veinminerEnabled);
            particlesObj.addProperty(key, value.particlesEnabled);

            JsonObject messageObj = new JsonObject();
            for (MessageType type : MessageType.values()) {
                messageObj.addProperty(type.id(), value.messageEnabled(type));
            }
            messagesObj.add(key, messageObj);

            JsonObject input = new JsonObject();
            input.addProperty("useKeybind", value.useKeybind);
            input.addProperty("keyToggle", value.keyToggleMode);
            input.addProperty("keyToggleState", value.keyToggleActive);
            input.addProperty("crouchToggle", value.crouchToggleMode);
            input.addProperty("crouchToggleState", value.crouchToggleActive);
            inputObj.add(key, input);
        }

        root.add("veinminer", veinminerObj);
        root.add("particles", particlesObj);
        root.add("messages", messagesObj);
        root.add("input", inputObj);
        return root;
    }

    private void ensureParentDirectory() {
        try {
            Files.createDirectories(dataFile.getParent());
        } catch (IOException ex) {
            Log.error("Failed to create player data directory", ex);
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            Log.warn("Invalid UUID '{}' in player data", raw);
            return null;
        }
    }

    private static final class PlayerSettings {
        boolean veinminerEnabled = true;
        boolean particlesEnabled = true;
        boolean useKeybind = true;
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
