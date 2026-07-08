package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class AllowedBlocksConfig extends TomlConfigFile {

    private static final Pattern ID_PATTERN = Pattern.compile("^#?[a-z0-9_.-]+:[a-z0-9_/.-]+$");

    private final NavigableSet<String> allowedBlocks = new TreeSet<>();

    public AllowedBlocksConfig() {
        super("BlocksList.toml");
    }

    public void load(Collection<String> defaults) {
        allowedBlocks.clear();
        try (CommentedFileConfig config = loadConfig()) {
            List<String> raw = config.getOrElse("blocks", new ArrayList<>(defaults));
            for (String entry : raw) {
                if (entry == null) continue;
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                if (!ID_PATTERN.matcher(trimmed).matches()) {
                    Log.warn("Ignoring malformed block entry '{}'", trimmed);
                    continue;
                }
                allowedBlocks.add(trimmed);
            }
            if (allowedBlocks.isEmpty()) {
                allowedBlocks.addAll(defaults);
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
        config.set("blocks", new ArrayList<>(allowedBlocks));
        config.setComment("blocks", "Block list entries by registry id or tag (prefix with '#' for tags). Mode controlled by advanced.block_list_mode");
    }

    public NavigableSet<String> values() {
        return Collections.unmodifiableNavigableSet(allowedBlocks);
    }

    public void replaceValues(Collection<String> newValues) {
        allowedBlocks.clear();
        allowedBlocks.addAll(newValues);
    }

    public boolean addValue(String value) {
        return allowedBlocks.add(value);
    }

    public boolean removeValue(String value) {
        return allowedBlocks.remove(value);
    }

    public boolean clear() {
        if (allowedBlocks.isEmpty()) {
            return false;
        }
        allowedBlocks.clear();
        return true;
    }
}

