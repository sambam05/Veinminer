package com.sheath.veinminer.config;

import com.sheath.veinminer.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ConfigFileFormatter {

    private ConfigFileFormatter() {
    }

    /**
     * Formats TOML arrays so each value appears on its own line, improving
     * readability for longer lists the mod produces (e.g. allowed blocks).
     */
    static void formatArraysOnePerLine(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> formatted = new ArrayList<>(lines.size());
            for (String line : lines) {
                int firstNonSpace = firstNonWhitespaceIndex(line);
                String indent = line.substring(0, firstNonSpace);
                String trimmed = line.substring(firstNonSpace);

                int start = trimmed.indexOf('[');
                int end = trimmed.lastIndexOf(']');
                boolean containsEquals = trimmed.contains("=");

                if (containsEquals && start >= 0 && end > start) {
                    formatted.add(indent + trimmed.substring(0, start + 1));
                    String body = trimmed.substring(start + 1, end).trim();
                    if (!body.isEmpty()) {
                        String[] parts = body.split(",");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i].trim();
                            boolean isLast = i == parts.length - 1;
                            formatted.add(indent + "    " + part + (isLast ? "" : ","));
                        }
                    }
                    formatted.add(indent + "]" + trimmed.substring(end + 1));
                } else {
                    formatted.add(line);
                }
            }
            Files.write(path, formatted, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Log.error("Failed to format config file " + path, ex);
        }
    }

    private static int firstNonWhitespaceIndex(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }
}

