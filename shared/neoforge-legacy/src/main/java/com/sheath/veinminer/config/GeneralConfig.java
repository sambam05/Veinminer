package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.util.Log;

import java.util.Locale;
import java.util.Objects;

/**
 * Handles persistence of high level configuration options that control feature
 * toggles and global tuning knobs.
 */
public final class GeneralConfig extends TomlConfigFile {

    public enum DurabilityMode {
        ABSOLUTE,
        PERCENTAGE;

        static DurabilityMode parse(String raw, DurabilityMode fallback) {
            try {
                return DurabilityMode.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                Log.warn("Unknown durability mode '{}', defaulting to {}", raw, fallback);
                return fallback;
            }
        }
    }

    public enum BlockListMode {
        GLOBAL_WHITELIST(false, false),
        PER_TOOL_WHITELIST(true, false),
        GLOBAL_BLACKLIST(false, true),
        PER_TOOL_BLACKLIST(true, true);

        private final boolean perTool;
        private final boolean blacklist;

        BlockListMode(boolean perTool, boolean blacklist) {
            this.perTool = perTool;
            this.blacklist = blacklist;
        }

        public boolean perTool() {
            return perTool;
        }

        public boolean blacklist() {
            return blacklist;
        }

        public boolean whitelist() {
            return !blacklist;
        }

        public static BlockListMode parse(String raw, BlockListMode fallback) {
            return parse(raw, fallback.perTool, fallback);
        }

        public static BlockListMode parse(String raw, boolean perToolFlag, BlockListMode fallback) {
            if (raw == null) {
                return from(perToolFlag, fallback.blacklist);
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            return switch (normalized) {
                case "WHITELIST", "WHITE", "WL", "GLOBAL_WHITELIST" -> from(perToolFlag, false);
                case "BLACKLIST", "BLACK", "BL", "GLOBAL_BLACKLIST" -> from(perToolFlag, true);
                case "PER_TOOL_WHITELIST", "PERTOOL_WHITELIST", "PER_TOOL" -> PER_TOOL_WHITELIST;
                case "PER_TOOL_BLACKLIST", "PERTOOL_BLACKLIST" -> PER_TOOL_BLACKLIST;
                default -> {
                    Log.warn("Unknown block list mode '{}', defaulting to {}", raw, fallback);
                    yield fallback;
                }
            };
        }

        public static BlockListMode from(boolean perToolFlag, boolean blacklistFlag) {
            if (perToolFlag) {
                return blacklistFlag ? PER_TOOL_BLACKLIST : PER_TOOL_WHITELIST;
            }
            return blacklistFlag ? GLOBAL_BLACKLIST : GLOBAL_WHITELIST;
        }
    }

    private boolean veinminerEnabled = true;
    private boolean requireCrouch = true;
    private boolean checkToolDurability = true;
    private int durabilityCap = 1;
    private DurabilityMode durabilityMode = DurabilityMode.ABSOLUTE;

    private int threadCount = 4;

    private final CooldownSettings cooldown = new CooldownSettings();
    private final BlockLimitSettings blockLimits = new BlockLimitSettings();
    private final ParticleSettings particles = new ParticleSettings();
    private final ExhaustionSettings exhaustion = new ExhaustionSettings();

    private BlockListMode blockListMode = BlockListMode.GLOBAL_WHITELIST;
    private boolean autoLuckPerms = false;
    private boolean setupWizardPromptComplete = false;

    public GeneralConfig() {
        super("GeneralConfig.toml");
    }

    public void load() {
        try (CommentedFileConfig config = loadConfig()) {
            veinminerEnabled = config.getOrElse("General.veinminerEnabled", veinminerEnabled);
            requireCrouch = config.getOrElse("General.requireCrouch", requireCrouch);
            checkToolDurability = config.getOrElse("General.checkToolDurability", checkToolDurability);
            durabilityCap = config.getOrElse("General.durabilityCap", durabilityCap);
            durabilityMode = DurabilityMode.parse(config.getOrElse("General.durabilityThreshold", durabilityMode.name()), durabilityMode);
            threadCount = Math.max(1, config.getOrElse("General.threadCount", threadCount));

            cooldown.enabled = config.getOrElse("Cooldown.enabled", cooldown.enabled);
            cooldown.seconds = Math.max(0, config.getOrElse("Cooldown.seconds", cooldown.seconds));

            blockLimits.dynamicMaxBlocks = config.getOrElse("BlockLimits.dynamicMaxBlocks", blockLimits.dynamicMaxBlocks);
            blockLimits.maxBlocks = Math.max(1, config.getOrElse("BlockLimits.maxBlocks", blockLimits.maxBlocks));
            blockLimits.minBlocks = Math.max(1, config.getOrElse("BlockLimits.minBlocks", blockLimits.minBlocks));
            blockLimits.maxDynamicBlocks = Math.max(blockLimits.minBlocks,
                    config.getOrElse("BlockLimits.maxDynamicBlocks", blockLimits.maxDynamicBlocks));

            particles.enabled = config.getOrElse("Particles.enabled", particles.enabled);
            particles.durationTicks = Math.max(1, config.getOrElse("Particles.durationTicks", particles.durationTicks));
            particles.red = clampColor(config.getOrElse("Particles.red", particles.red), "Particles.red");
            particles.green = clampColor(config.getOrElse("Particles.green", particles.green), "Particles.green");
            particles.blue = clampColor(config.getOrElse("Particles.blue", particles.blue), "Particles.blue");

            exhaustion.enabled = config.getOrElse("Exhaustion.enabled", exhaustion.enabled);
            exhaustion.scale = clampNonNegative(readDouble(config, "Exhaustion.scale", exhaustion.scale), "Exhaustion.scale");

            boolean blocksPerTool = config.getOrElse("Advanced.blocksPerTool", blockListMode.perTool());
            if (config.contains("Advanced.blockListMode")) {
                blockListMode = BlockListMode.parse(config.get("Advanced.blockListMode"), blocksPerTool, blockListMode);
            } else {
                blockListMode = BlockListMode.from(blocksPerTool, blockListMode.blacklist());
            }
            autoLuckPerms = config.getOrElse("Integration.autoLuckPerms", autoLuckPerms);
            setupWizardPromptComplete = config.getOrElse("Advanced.setupWizardPromptComplete", setupWizardPromptComplete);

            writeBack(config);
            save(config);
        }
    }

    public void save() {
        try (CommentedFileConfig config = loadConfig()) {
            writeBack(config);
            save(config);
        }
    }

    private void writeBack(CommentedFileConfig config) {
        config.set("General.veinminerEnabled", veinminerEnabled);
        config.setComment("General.veinminerEnabled", "Enable or disable Veinminer entirely");

        config.set("General.requireCrouch", requireCrouch);
        config.setComment("General.requireCrouch", "Require the player to crouch (sneak) to activate Veinminer");

        config.set("General.checkToolDurability", checkToolDurability);
        config.setComment("General.checkToolDurability", "Prevent Veinminer from activating if the tool would break");

        config.set("General.durabilityCap", durabilityCap);
        config.setComment("General.durabilityCap", "Minimum durability to keep when Veinminer activates");

        config.set("General.durabilityThreshold", durabilityMode.name());
        config.setComment("General.durabilityThreshold", "ABSOLUTE keeps a fixed number of durability points, PERCENTAGE keeps a percentage");

        config.set("General.threadCount", threadCount);
        config.setComment("General.threadCount", "Number of worker threads used for asynchronous operations");

        config.set("Cooldown.enabled", cooldown.enabled);
        config.setComment("Cooldown.enabled", "Enable a cooldown between Veinminer uses");

        config.set("Cooldown.seconds", cooldown.seconds);
        config.setComment("Cooldown.seconds", "Cooldown duration in seconds");

        config.set("BlockLimits.dynamicMaxBlocks", blockLimits.dynamicMaxBlocks);
        config.setComment("BlockLimits.dynamicMaxBlocks", "Dynamically adjust max mined blocks based on server TPS");

        config.set("BlockLimits.maxBlocks", blockLimits.maxBlocks);
        config.setComment("BlockLimits.maxBlocks", "Maximum blocks mined when dynamic scaling is disabled");

        config.set("BlockLimits.minBlocks", blockLimits.minBlocks);
        config.setComment("BlockLimits.minBlocks", "Lower bound for dynamic scaling");

        config.set("BlockLimits.maxDynamicBlocks", blockLimits.maxDynamicBlocks);
        config.setComment("BlockLimits.maxDynamicBlocks", "Upper bound for dynamic scaling");

        config.set("Particles.enabled", particles.enabled);
        config.setComment("Particles.enabled", "Render an outline around blocks scheduled to be mined");

        config.set("Particles.durationTicks", particles.durationTicks);
        config.setComment("Particles.durationTicks", "Particle lifespan in ticks");

        config.set("Particles.red", particles.red);
        config.set("Particles.green", particles.green);
        config.set("Particles.blue", particles.blue);

        config.set("Exhaustion.enabled", exhaustion.enabled);
        config.setComment("Exhaustion.enabled", "Apply hunger exhaustion for blocks broken by Veinminer");

        config.set("Exhaustion.scale", exhaustion.scale);
        config.setComment("Exhaustion.scale", "Exhaustion multiplier. 1.0 = vanilla per-block exhaustion, 0.5 = half, 2.0 = double");

        config.set("Advanced.blockListMode", blockListMode.blacklist() ? "BLACKLIST" : "WHITELIST");
        config.setComment("Advanced.blockListMode",
                "Use WHITELIST or BLACKLIST. The blockPerTool toggle decides whether the lists are global or per-tool.");
        config.set("Advanced.blocksPerTool", blockListMode.perTool());
        config.setComment("Advanced.blocksPerTool", "If true, block lists are per-tool; if false, a single global list is used.");

        config.set("Advanced.setupWizardPromptComplete", setupWizardPromptComplete);
        config.setComment("Advanced.setupWizardPromptComplete", "If false, admins will be prompted on login to run /veinminer setup.");

        config.set("Integration.autoLuckPerms", autoLuckPerms);
        config.setComment("Integration.autoLuckPerms", "Attempt to hook into LuckPerms automatically if present");
    }

    private int clampColor(int value, String name) {
        if (value < 0 || value > 255) {
            int clamped = Math.max(0, Math.min(255, value));
            Log.warn("{}={} is outside the 0-255 range. Clamping to {}", name, value, clamped);
            return clamped;
        }
        return value;
    }

    private double readDouble(CommentedFileConfig config, String key, double fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private double clampNonNegative(double value, String name) {
        if (value < 0.0) {
            Log.warn("{}={} is negative. Clamping to 0", name, value);
            return 0.0;
        }
        return value;
    }

    public boolean veinminerEnabled() {
        return veinminerEnabled;
    }

    public void setVeinminerEnabled(boolean veinminerEnabled) {
        this.veinminerEnabled = veinminerEnabled;
    }

    public boolean requireCrouch() {
        return requireCrouch;
    }

    public void setRequireCrouch(boolean requireCrouch) {
        this.requireCrouch = requireCrouch;
    }

    public boolean checkToolDurability() {
        return checkToolDurability;
    }

    public void setCheckToolDurability(boolean checkToolDurability) {
        this.checkToolDurability = checkToolDurability;
    }

    public int durabilityCap() {
        return durabilityCap;
    }

    public void setDurabilityCap(int durabilityCap) {
        this.durabilityCap = Math.max(0, durabilityCap);
    }

    public DurabilityMode durabilityMode() {
        return durabilityMode;
    }

    public void setDurabilityMode(DurabilityMode durabilityMode) {
        this.durabilityMode = durabilityMode;
    }

    public int threadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = Math.max(1, threadCount);
    }

    public CooldownSettings cooldown() {
        return cooldown;
    }

    public BlockLimitSettings blockLimits() {
        return blockLimits;
    }

    public ParticleSettings particles() {
        return particles;
    }

    public ExhaustionSettings exhaustion() {
        return exhaustion;
    }

    public BlockListMode blockListMode() {
        return blockListMode;
    }

    public void setBlockListMode(BlockListMode blockListMode) {
        this.blockListMode = Objects.requireNonNull(blockListMode, "blockListMode");
    }

    public boolean autoLuckPerms() {
        return autoLuckPerms;
    }

    public void setAutoLuckPerms(boolean autoLuckPerms) {
        this.autoLuckPerms = autoLuckPerms;
    }

    public boolean setupWizardPromptComplete() {
        return setupWizardPromptComplete;
    }

    public void setSetupWizardPromptComplete(boolean setupWizardPromptComplete) {
        this.setupWizardPromptComplete = setupWizardPromptComplete;
    }

    public int durabilityReserveFor(int maxDurability) {
        if (durabilityMode == DurabilityMode.PERCENTAGE) {
            double percentage = Math.max(0, Math.min(100, durabilityCap));
            return (int) Math.ceil((percentage / 100.0) * maxDurability);
        }
        return Math.max(0, durabilityCap);
    }

    public static final class CooldownSettings {
        private boolean enabled = false;
        private int seconds = 5;

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int seconds() {
            return seconds;
        }

        public void setSeconds(int seconds) {
            this.seconds = Math.max(0, seconds);
        }
    }

    public static final class BlockLimitSettings {
        private boolean dynamicMaxBlocks = false;
        private int maxBlocks = 64;
        private int minBlocks = 16;
        private int maxDynamicBlocks = 64;

        public boolean dynamicMaxBlocks() {
            return dynamicMaxBlocks;
        }

        public void setDynamicMaxBlocks(boolean dynamicMaxBlocks) {
            this.dynamicMaxBlocks = dynamicMaxBlocks;
        }

        public int maxBlocks() {
            return maxBlocks;
        }

        public void setMaxBlocks(int maxBlocks) {
            this.maxBlocks = Math.max(1, maxBlocks);
        }

        public int minBlocks() {
            return minBlocks;
        }

        public void setMinBlocks(int minBlocks) {
            this.minBlocks = Math.max(1, minBlocks);
        }

        public int maxDynamicBlocks() {
            return maxDynamicBlocks;
        }

        public void setMaxDynamicBlocks(int maxDynamicBlocks) {
            this.maxDynamicBlocks = Math.max(minBlocks, maxDynamicBlocks);
        }
    }

    public static final class ParticleSettings {
        private boolean enabled = true;
        private int durationTicks = 60;
        private int red = 255;
        private int green = 0;
        private int blue = 0;

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public void setDurationTicks(int durationTicks) {
            this.durationTicks = Math.max(1, durationTicks);
        }

        public int red() {
            return red;
        }

        public void setRed(int red) {
            this.red = Math.max(0, Math.min(255, red));
        }

        public int green() {
            return green;
        }

        public void setGreen(int green) {
            this.green = Math.max(0, Math.min(255, green));
        }

        public int blue() {
            return blue;
        }

        public void setBlue(int blue) {
            this.blue = Math.max(0, Math.min(255, blue));
        }
    }

    public static final class ExhaustionSettings {
        private boolean enabled = false;
        private double scale = 1.0;

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double scale() {
            return scale;
        }

        public void setScale(double scale) {
            this.scale = Math.max(0.0, scale);
        }
    }
}
