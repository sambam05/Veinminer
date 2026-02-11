package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class AllowedToolsConfig extends TomlConfigFile {

    private static final Pattern ID_PATTERN = Pattern.compile("^#?[a-z0-9_.-]+:[a-z0-9_/.-]+$");

    private final NavigableSet<String> allowedTools = new TreeSet<>();

    public AllowedToolsConfig() {
        super("ToolsList.toml");
    }

    public void load(Collection<String> defaults) {
        allowedTools.clear();
        try (CommentedFileConfig config = loadConfig()) {
            List<String> raw = config.get("tools");
            if (raw == null) {
                allowedTools.addAll(defaults);
            } else {
                for (String entry : raw) {
                    if (entry == null) continue;
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty()) continue;
                    String lower = trimmed.toLowerCase(Locale.ROOT);
                    if (!ConfigService.HAND_KEY.equals(lower) && !ID_PATTERN.matcher(trimmed).matches()) {
                        Log.warn("Ignoring malformed tool entry '{}'", trimmed);
                        continue;
                    }
                    allowedTools.add(ConfigService.HAND_KEY.equals(lower) ? ConfigService.HAND_KEY : trimmed);
                }
                if (allowedTools.isEmpty()) {
                    allowedTools.addAll(defaults);
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
        config.set("tools", new ArrayList<>(allowedTools));
        config.setComment("tools",
                "Allowed tool registry ids or tag strings starting with '#'. Use 'hand' to permit empty-hand veinmining.");
    }

    public NavigableSet<String> values() {
        return Collections.unmodifiableNavigableSet(allowedTools);
    }

    public void replaceValues(Collection<String> newValues) {
        allowedTools.clear();
        allowedTools.addAll(newValues);
    }

    public boolean addValue(String value) {
        return allowedTools.add(value);
    }

    public boolean removeValue(String value) {
        return allowedTools.remove(value);
    }

    public boolean clear() {
        if (allowedTools.isEmpty()) {
            return false;
        }
        allowedTools.clear();
        return true;
    }
}
