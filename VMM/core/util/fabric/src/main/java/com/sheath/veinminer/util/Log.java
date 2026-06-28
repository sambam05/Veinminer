package com.sheath.veinminer.util;

import com.sheath.veinminer.core.ModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight logging facade that centralises formatting and shields the rest
 * of the codebase from directly depending on SLF4J. Using a dedicated helper
 * simplifies testing and enables consistent prefixes.
 */
public final class Log {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_NAME);
    private static final String PREFIX = "[" + ModConstants.MOD_NAME + "] ";

    private Log() {
        // utility class
    }

    public static void info(String message, Object... args) {
        LOGGER.info(PREFIX + message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(PREFIX + message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(PREFIX + message, args);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(PREFIX + message, throwable);
    }

    public static void debug(String message, Object... args) {
        LOGGER.debug(PREFIX + message, args);
    }

    public static void trace(String message, Object... args) {
        LOGGER.trace(PREFIX + message, args);
    }
}

