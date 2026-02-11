package com.sheath.veinminer.config;

import java.nio.file.Path;

/**
 * Wraps config parsing failures with file and line information when available.
 */
public final class ConfigParseException extends RuntimeException {

    private final Path path;
    private final int line;

    public ConfigParseException(Path path, int line, String message, Throwable cause) {
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
