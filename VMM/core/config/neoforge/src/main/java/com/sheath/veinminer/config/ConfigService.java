package com.sheath.veinminer.config;

import com.sheath.veinminer.util.Log;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates config loading and exposes derived, ready-to-use views for the
 * rest of the mod.
 */
public final class ConfigService {

    public static final String HAND_KEY = "hand";

    private final GeneralConfig general = new GeneralConfig();
    private final AllowedToolsConfig tools = new AllowedToolsConfig();
    private final AllowedBlocksConfig blocks = new AllowedBlocksConfig();
    private final BlocksPerToolConfig blocksPerTool = new BlocksPerToolConfig();

    private volatile ConfigSnapshot snapshot;

    public enum ChangeResult {
        SUCCESS,
        ALREADY_PRESENT,
        NOT_FOUND,
        INVALID
    }

    public void loadAll() {
        tryLoad(general.path(), general::load);

        Collection<String> blockDefaults = general.blockListMode().whitelist()
                ? defaultBlockIds()
                : Collections.emptyList();
        Map<String, ? extends Collection<String>> perToolDefaults = general.blockListMode().perTool() && general.blockListMode().whitelist()
                ? defaultBlocksPerTool()
                : Collections.emptyMap();

        tryLoad(tools.path(), () -> tools.load(defaultToolIds()));
        tryLoad(blocks.path(), () -> blocks.load(blockDefaults));
        tryLoad(blocksPerTool.path(), () -> blocksPerTool.load(perToolDefaults));
        snapshot = buildSnapshot();
    }

    public void saveAll() {
        general.save();
        tools.save();
        blocks.save();
        blocksPerTool.save();
        snapshot = buildSnapshot();
    }

    private void tryLoad(Path path, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            throw unwrap(path, ex);
        }
    }

    public ConfigSnapshot snapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("Configs not loaded yet");
        }
        return snapshot;
    }

    private ConfigLoadException unwrap(Path path, RuntimeException ex) {
        int line = extractLine(ex);
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return new ConfigLoadException(path, line, message, ex);
    }

    public static final class ConfigLoadException extends RuntimeException {
        private final Path path;
        private final int line;

        public ConfigLoadException(Path path, int line, String message, Throwable cause) {
            super(message, cause);
            this.path = path;
            this.line = line;
        }

        public Path path() {
            return path;
        }

        public int line() {
            return line;
        }
    }

    private static final Pattern LINE_PATTERN = Pattern.compile("(?:line|line:\\s*)(\\d+)", Pattern.CASE_INSENSITIVE);

    private int extractLine(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = LINE_PATTERN.matcher(message);
                if (matcher.find()) {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            current = current.getCause();
        }
        return -1;
    }

    public GeneralConfig general() {
        return general;
    }

    public AllowedToolsConfig toolsConfig() {
        return tools;
    }

    public AllowedBlocksConfig blocksConfig() {
        return blocks;
    }

    public BlocksPerToolConfig blocksPerToolConfig() {
        return blocksPerTool;
    }

    public ChangeResult addAllowedBlock(String entry) {
        String normalized = normalizeReference(entry);
        if (!isValidRegistryReference(normalized)) {
            return ChangeResult.INVALID;
        }
        if (!blocks.addValue(normalized)) {
            return ChangeResult.ALREADY_PRESENT;
        }
        blocks.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult removeAllowedBlock(String entry) {
        String normalized = normalizeReference(entry);
        if (!isValidRegistryReference(normalized)) {
            return ChangeResult.INVALID;
        }
        if (!blocks.removeValue(normalized)) {
            return ChangeResult.NOT_FOUND;
        }
        blocks.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult clearAllowedBlocks() {
        if (!blocks.clear()) {
            return ChangeResult.NOT_FOUND;
        }
        blocks.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult addAllowedTool(String entry) {
        String normalized = normalizeReference(entry);
        boolean hand = isHandReference(normalized);
        if (!hand && !isValidRegistryReference(normalized)) {
            return ChangeResult.INVALID;
        }
        if (!tools.addValue(normalized)) {
            return ChangeResult.ALREADY_PRESENT;
        }
        tools.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult removeAllowedTool(String entry) {
        String normalized = normalizeReference(entry);
        boolean hand = isHandReference(normalized);
        if (!hand && !isValidRegistryReference(normalized)) {
            return ChangeResult.INVALID;
        }
        if (!tools.removeValue(normalized)) {
            return ChangeResult.NOT_FOUND;
        }
        tools.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult clearAllowedTools() {
        if (!tools.clear()) {
            return ChangeResult.NOT_FOUND;
        }
        tools.save();
        snapshot = buildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult addBlocksPerToolTool(String entry) {
        String tool = normalizeReference(entry);
        boolean hand = isHandReference(tool);
        if (!hand && !isValidRegistryReference(tool)) {
            return ChangeResult.INVALID;
        }
        if (blocksPerTool.containsTool(tool)) {
            return ChangeResult.ALREADY_PRESENT;
        }
        blocksPerTool.getOrCreate(tool);
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult removeBlocksPerToolTool(String entry) {
        String tool = normalizeReference(entry);
        boolean hand = isHandReference(tool);
        if (!hand && !isValidRegistryReference(tool)) {
            return ChangeResult.INVALID;
        }
        if (!blocksPerTool.removeTool(tool)) {
            return ChangeResult.NOT_FOUND;
        }
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult addBlocksPerToolBlock(String toolEntry, String blockEntry) {
        String tool = normalizeReference(toolEntry);
        String block = normalizeReference(blockEntry);
        boolean hand = isHandReference(tool);
        if ((!hand && !isValidRegistryReference(tool)) || !isValidRegistryReference(block)) {
            return ChangeResult.INVALID;
        }
        NavigableSet<String> blocksForTool = blocksPerTool.getOrCreate(tool);
        if (!blocksForTool.add(block)) {
            return ChangeResult.ALREADY_PRESENT;
        }
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult removeBlocksPerToolBlock(String toolEntry, String blockEntry) {
        String tool = normalizeReference(toolEntry);
        String block = normalizeReference(blockEntry);
        boolean hand = isHandReference(tool);
        if ((!hand && !isValidRegistryReference(tool)) || !isValidRegistryReference(block)) {
            return ChangeResult.INVALID;
        }
        NavigableSet<String> blocksForTool = blocksPerTool.get(tool);
        if (blocksForTool == null || !blocksForTool.remove(block)) {
            return ChangeResult.NOT_FOUND;
        }
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult clearBlocksForTool(String toolEntry) {
        String tool = normalizeReference(toolEntry);
        boolean hand = isHandReference(tool);
        if ((!hand && !isValidRegistryReference(tool)) || tool.isEmpty()) {
            return ChangeResult.INVALID;
        }
        if (!blocksPerTool.clearBlocksFor(tool)) {
            return ChangeResult.NOT_FOUND;
        }
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public ChangeResult clearBlocksPerTool() {
        if (!blocksPerTool.clearAll()) {
            return ChangeResult.NOT_FOUND;
        }
        blocksPerTool.save();
        rebuildSnapshot();
        return ChangeResult.SUCCESS;
    }

    public NavigableSet<String> getBlocksForTool(String toolEntry) {
        String tool = normalizeReference(toolEntry);
        boolean hand = isHandReference(tool);
        if (!hand && !isValidRegistryReference(tool)) {
            return null;
        }
        return snapshot().blocksPerTool().rawMapping().get(tool);
    }

    public NavigableSet<String> getAllToolKeys() {
        return snapshot().blocksPerTool().rawMapping().navigableKeySet();
    }

    public void rebuildSnapshot() {
        snapshot = buildSnapshot();
    }

    private ConfigSnapshot buildSnapshot() {
        RegistryList<Item> toolRules = parseRegistryList(tools.values(), Registries.ITEM, true);
        RegistryList<Block> blockRules = parseRegistryList(blocks.values(), Registries.BLOCK, false);
        BlocksPerToolRules perToolRules = buildBlocksPerToolRules(blocksPerTool.values());
        return new ConfigSnapshot(general, toolRules, blockRules, perToolRules);
    }

    private BlocksPerToolRules buildBlocksPerToolRules(NavigableMap<String, NavigableSet<String>> entries) {
        LinkedHashMap<Object, RegistryList<Block>> byToolId = new LinkedHashMap<>();
        LinkedHashMap<TagKey<Item>, RegistryList<Block>> byToolTag = new LinkedHashMap<>();
        RegistryList<Block> handRules = null;

        for (var entry : entries.entrySet()) {
            String toolKey = entry.getKey();
            if (toolKey == null || toolKey.isBlank()) continue;

            String trimmed = toolKey.trim();
            boolean isTag = trimmed.startsWith("#");
            String idString = isTag ? trimmed.substring(1) : trimmed;
            if (isHandReference(trimmed)) {
                handRules = parseRegistryList(entry.getValue(), Registries.BLOCK, false);
                continue;
            }
            Object toolId = parseIdentifier(idString);
            if (toolId == null) {
                Log.warn("Invalid tool identifier '{}' in blocks-per-tool config", toolKey);
                continue;
            }

            RegistryList<Block> parsed = parseRegistryList(entry.getValue(), Registries.BLOCK, false);
            if (isTag) {
                TagKey<Item> toolTag = createTagKey(Registries.ITEM, toolId);
                if (toolTag == null) {
                    Log.warn("Invalid tool tag '{}' in blocks-per-tool config", toolKey);
                    continue;
                }
                byToolTag.put(toolTag, parsed);
            } else {
                byToolId.put(toolId, parsed);
            }
        }

        return new BlocksPerToolRules(
                Collections.unmodifiableMap(byToolId),
                Collections.unmodifiableMap(byToolTag),
                entries,
                handRules
        );
    }

    private <T> RegistryList<T> parseRegistryList(Collection<String> values,
                                                  ResourceKey<? extends Registry<T>> registryKey,
                                                  boolean allowHandKeyword) {
        NavigableSet<String> raw = new TreeSet<>();
        Set<Object> identifiers = new LinkedHashSet<>();
        Set<TagKey<T>> tags = new LinkedHashSet<>();
        boolean allowHand = false;

        for (String entry : values) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            raw.add(trimmed);

            if (allowHandKeyword && isHandReference(trimmed)) {
                allowHand = true;
                continue;
            }

            boolean isTag = trimmed.startsWith("#");
            String idString = isTag ? trimmed.substring(1) : trimmed;
            Object id = parseIdentifier(idString);
            if (id == null) {
                Log.warn("Invalid identifier '{}' in {} config", trimmed, registryKeyName(registryKey));
                continue;
            }
            if (isTag) {
                TagKey<T> tagKey = createTagKey(registryKey, id);
                if (tagKey == null) {
                    Log.warn("Invalid tag '{}' in {} config", trimmed, registryKeyName(registryKey));
                    continue;
                }
                tags.add(tagKey);
            } else {
                identifiers.add(id);
            }
        }

        return new RegistryList<>(
                Collections.unmodifiableNavigableSet(raw),
                Collections.unmodifiableSet(identifiers),
                Collections.unmodifiableSet(tags),
                allowHandKeyword && allowHand
        );
    }

    private static Collection<String> defaultToolIds() {
        List<String> defaults = new ArrayList<>();
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.WOODEN_PICKAXE).toString());
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.STONE_PICKAXE).toString());
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE).toString());
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.GOLDEN_PICKAXE).toString());
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE).toString());
        defaults.add(BuiltInRegistries.ITEM.getKey(Items.NETHERITE_PICKAXE).toString());
        return defaults;
    }

    private static Collection<String> defaultBlockIds() {
        List<String> defaults = new ArrayList<>();
        defaults.add("#minecraft:logs");
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.COAL_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.IRON_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.COPPER_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.GOLD_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.REDSTONE_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.LAPIS_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.EMERALD_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DIAMOND_ORE).toString());

        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_COAL_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_IRON_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_COPPER_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_GOLD_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_REDSTONE_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_LAPIS_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_EMERALD_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_DIAMOND_ORE).toString());

        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.NETHER_GOLD_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.NETHER_QUARTZ_ORE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.GILDED_BLACKSTONE).toString());
        defaults.add(BuiltInRegistries.BLOCK.getKey(Blocks.ANCIENT_DEBRIS).toString());
        return defaults;
    }

    private static NavigableMap<String, NavigableSet<String>> defaultBlocksPerTool() {
        NavigableMap<String, NavigableSet<String>> map = new TreeMap<>();
        Collection<String> blocks = defaultBlockIds();
        for (String tool : defaultToolIds()) {
            map.put(tool, new TreeSet<>(blocks));
        }
        return map;
    }

    private String normalizeReference(String entry) {
        if (entry == null) {
            return "";
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (HAND_KEY.equalsIgnoreCase(trimmed)) {
            return HAND_KEY;
        }
        if (trimmed.startsWith("#")) {
            String value = trimmed.substring(1).trim();
            if (value.isEmpty()) {
                return "";
            }
            return "#" + value.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean isHandReference(String entry) {
        return entry != null && HAND_KEY.equalsIgnoreCase(entry);
    }

    private boolean isValidRegistryReference(String entry) {
        return isValidRegistryReference(entry, false);
    }

    private boolean isValidRegistryReference(String entry, boolean allowHand) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        if (allowHand && isHandReference(entry)) {
            return true;
        }
        if (entry.startsWith("#")) {
            if (entry.length() == 1) return false;
            return parseIdentifier(entry.substring(1)) != null;
        }
        return parseIdentifier(entry) != null;
    }

    private static Object parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        Object parsed = tryParseIdentifier("net.minecraft.resources.ResourceLocation", value);
        if (parsed != null) {
            return parsed;
        }
        return tryParseIdentifier("net.minecraft.resources.Identifier", value);
    }

    private static Object tryParseIdentifier(String className, String value) {
        try {
            Class<?> identifierClass = Class.forName(className);

            for (Method method : identifierClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) {
                    continue;
                }
                if (!identifierClass.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                String name = method.getName();
                if (!"tryParse".equals(name) && !"parse".equals(name) && !"fromString".equals(name) && !"of".equals(name)) {
                    continue;
                }
                try {
                    return method.invoke(null, value);
                } catch (ReflectiveOperationException ignored) {
                }
            }

            int separator = value.indexOf(':');
            if (separator > 0 && separator < value.length() - 1) {
                Method fromNamespaceAndPath = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                return fromNamespaceAndPath.invoke(null, value.substring(0, separator), value.substring(separator + 1));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static <T> TagKey<T> createTagKey(ResourceKey<? extends Registry<T>> registryKey, Object identifier) {
        if (identifier == null) {
            return null;
        }

        try {
            for (Method method : TagKey.class.getMethods()) {
                if (!"create".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterCount() != 2) {
                    continue;
                }
                try {
                    Object result = method.invoke(null, registryKey, identifier);
                    if (result instanceof TagKey<?> tagKey) {
                        @SuppressWarnings("unchecked")
                        TagKey<T> typed = (TagKey<T>) tagKey;
                        return typed;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static String registryKeyName(ResourceKey<?> registryKey) {
        try {
            Method location = registryKey.getClass().getMethod("location");
            Object value = location.invoke(registryKey);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method getValue = registryKey.getClass().getMethod("getValue");
            Object value = getValue.invoke(registryKey);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return registryKey.toString();
    }

    public record ConfigSnapshot(
            GeneralConfig general,
            RegistryList<Item> allowedTools,
            RegistryList<Block> allowedBlocks,
            BlocksPerToolRules blocksPerTool) {
        public ConfigSnapshot {
            Objects.requireNonNull(general, "general");
            Objects.requireNonNull(allowedTools, "allowedTools");
            Objects.requireNonNull(allowedBlocks, "allowedBlocks");
            Objects.requireNonNull(blocksPerTool, "blocksPerTool");
        }
    }

    public static final class RegistryList<T> {
        private final NavigableSet<String> raw;
        private final Set<Object> identifiers;
        private final Set<TagKey<T>> tags;
        private final boolean allowEmptyHand;

        private RegistryList(NavigableSet<String> raw,
                             Set<Object> identifiers,
                             Set<TagKey<T>> tags,
                             boolean allowEmptyHand) {
            this.raw = raw;
            this.identifiers = identifiers;
            this.tags = tags;
            this.allowEmptyHand = allowEmptyHand;
        }

        public NavigableSet<String> raw() {
            return raw;
        }

        public Set<Object> identifiers() {
            return identifiers;
        }

        public Set<TagKey<T>> tags() {
            return tags;
        }

        public boolean allowEmptyHand() {
            return allowEmptyHand;
        }
    }

    public static final class BlocksPerToolRules {
        private final Map<Object, RegistryList<Block>> byToolId;
        private final Map<TagKey<Item>, RegistryList<Block>> byToolTag;
        private final NavigableMap<String, NavigableSet<String>> rawMapping;
        private final RegistryList<Block> handRules;

        private BlocksPerToolRules(Map<Object, RegistryList<Block>> byToolId,
                                   Map<TagKey<Item>, RegistryList<Block>> byToolTag,
                                   NavigableMap<String, NavigableSet<String>> rawMapping,
                                   RegistryList<Block> handRules) {
            this.byToolId = byToolId;
            this.byToolTag = byToolTag;
            this.handRules = handRules;

            NavigableMap<String, NavigableSet<String>> copy = new TreeMap<>();
            for (var entry : rawMapping.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableNavigableSet(new TreeSet<>(entry.getValue())));
            }
            this.rawMapping = Collections.unmodifiableNavigableMap(copy);
        }

        public Map<Object, RegistryList<Block>> byToolId() {
            return byToolId;
        }

        public Map<TagKey<Item>, RegistryList<Block>> byToolTag() {
            return byToolTag;
        }

        public NavigableMap<String, NavigableSet<String>> rawMapping() {
            return rawMapping;
        }

        public RegistryList<Block> handRules() {
            return handRules;
        }

        public RegistryList<Block> forTool(Object toolId) {
            return byToolId.get(toolId);
        }

        public RegistryList<Block> forTool(TagKey<Item> toolTag) {
            return byToolTag.get(toolTag);
        }
    }
}
