package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.util.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class TomlConfigFile {

    private final Path path;

    protected TomlConfigFile(String fileName) {
        this.path = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY)
                .resolve(fileName);
    }

    protected CommentedFileConfig loadConfig() {
        ensureParentExists();
        CommentedFileConfig config = CommentedFileConfig.builder(path)
                .autosave()
                .sync()
                .preserveInsertionOrder()
                .build();
        config.load();
        return config;
    }

    protected void save(CommentedFileConfig config) {
        config.save();
    }

    public Path path() {
        return path;
    }

    private void ensureParentExists() {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                Log.error("Failed to create config directory " + parent, ex);
            }
        }
    }
}
