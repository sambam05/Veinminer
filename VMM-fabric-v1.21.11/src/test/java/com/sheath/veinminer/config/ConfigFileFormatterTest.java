package com.sheath.veinminer.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigFileFormatterTest {

    @Test
    void formatsInlineTomlArraysOneEntryPerLine() throws Exception {
        Path temp = Files.createTempFile("veinminer-config", ".toml");
        try {
            Files.write(temp, List.of(
                    "allowed = [\"minecraft:stone\", \"minecraft:dirt\", \"#c:ores\"]",
                    "name = \"kept\""
            ), StandardCharsets.UTF_8);

            ConfigFileFormatter.formatArraysOnePerLine(temp);

            List<String> lines = Files.readAllLines(temp, StandardCharsets.UTF_8);
            assertEquals(List.of(
                    "allowed = [",
                    "    \"minecraft:stone\",",
                    "    \"minecraft:dirt\",",
                    "    \"#c:ores\"",
                    "]",
                    "name = \"kept\""
            ), lines);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
