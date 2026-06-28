package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class BlocksPerToolConfig extends TomlConfigFile {

    private final NavigableMap<String, NavigableSet<String>> allowed = new TreeMap<>();

    public BlocksPerToolConfig() {
        super("BlockPerToolList.toml");
    }

    public void load(Map<String, ? extends Collection<String>> defaults) {
        allowed.clear();
        try (CommentedFileConfig config = loadConfig()) {
            if (config.isEmpty()) {
                defaults.forEach((key, values) -> allowed.put(key, new TreeSet<>(values)));
            } else {
                for (String key : config.valueMap().keySet()) {
                    Object raw = config.get(key);
                    NavigableSet<String> values = new TreeSet<>();
                    if (raw instanceof Iterable<?> iterable) {
                        for (Object element : iterable) {
                            if (element == null) continue;
                            values.add(element.toString().trim());
                        }
                    } else if (raw != null) {
                        values.add(raw.toString().trim());
                    }
                    allowed.put(key, values);
                }
            }
            writeBack(config);
            save(config);
        }
        ConfigFileFormatter.formatArraysOnePerLine(path());
    }

    public void save() {
        try (CommentedFileConfig config = loadConfig()) {
            writeBack(config);
            save(config);
        }
        ConfigFileFormatter.formatArraysOnePerLine(path());
    }

    private void writeBack(CommentedFileConfig config) {
        config.clear();
        config.setComment("",
                "Mapping of tool registry ids or tag strings to block list entries (ids or '#tag').\n" +
                        "Use '" + ConfigService.HAND_KEY + "' to target empty-hand veinmining.\n" +
                        "Acts as a whitelist or blacklist depending on advanced.block_list_mode.\n" +
                        "Example:\n" +
                        "  \"minecraft:iron_pickaxe\" = [\"minecraft:diamond_ore\", \"#forge:ores\"]");
        for (Map.Entry<String, NavigableSet<String>> entry : allowed.entrySet()) {
            config.set(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public NavigableMap<String, NavigableSet<String>> values() {
        return Collections.unmodifiableNavigableMap(allowed);
    }

    public NavigableSet<String> getOrCreate(String toolKey) {
        return allowed.computeIfAbsent(toolKey, key -> new TreeSet<>());
    }

    public boolean containsTool(String toolKey) {
        return allowed.containsKey(toolKey);
    }

    public NavigableSet<String> get(String toolKey) {
        return allowed.get(toolKey);
    }

    public void replaceAll(Map<String, ? extends Collection<String>> replacements) {
        allowed.clear();
        replacements.forEach((key, values) -> allowed.put(key, new TreeSet<>(values)));
    }

    public boolean removeTool(String toolKey) {
        return allowed.remove(toolKey) != null;
    }

    public boolean clearAll() {
        if (allowed.isEmpty()) {
            return false;
        }
        allowed.clear();
        return true;
    }

    public boolean clearBlocksFor(String toolKey) {
        NavigableSet<String> values = allowed.get(toolKey);
        if (values == null || values.isEmpty()) {
            return false;
        }
        values.clear();
        return true;
    }
}

