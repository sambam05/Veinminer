package com.sheath.veinminer.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sheath.veinminer.util.Log;

import java.util.Locale;
import java.util.Objects;

/**
 * Central config grouped for an easy default user experience:
 * - basic: core gameplay behaviour
 * - visual: client-facing feedback controls
 * - advanced: admin/debug/experimental controls
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

    private final BasicSettings basic = new BasicSettings();
    private final VisualSettings visual = new VisualSettings();
    private final AdvancedSettings advanced = new AdvancedSettings();

    private final CooldownSettings cooldown = new CooldownSettings();
    private final BlockLimitSettings blockLimits = new BlockLimitSettings();
    private final ParticleSettings particles = new ParticleSettings();
    private final ExhaustionSettings exhaustion = new ExhaustionSettings();

    private BlockListMode blockListMode = BlockListMode.GLOBAL_WHITELIST;
    private boolean autoLuckPerms = false;
    private int threadCount = 2;

    private boolean setupWizardPromptComplete = true;

    public GeneralConfig() {
        super("GeneralConfig.toml");
    }

    public void load() {
        try (CommentedFileConfig config = loadConfig()) {
            basic.enabled = config.getOrElse("basic.enabled",
                    config.getOrElse("General.veinminerEnabled", basic.enabled));

            basic.activationKey = normalizeActivationKey(config.getOrElse("basic.activation_key",
                    config.getOrElse("General.requireCrouch", true) ? "SHIFT" : "ALWAYS"));

            basic.requireCorrectTool = config.getOrElse("basic.require_correct_tool", basic.requireCorrectTool);
            basic.checkToolDurability = config.getOrElse("basic.check_tool_durability",
                    config.getOrElse("General.checkToolDurability", basic.checkToolDurability));
            basic.durabilityCap = Math.max(0, config.getOrElse("basic.durability_cap",
                    config.getOrElse("General.durabilityCap", basic.durabilityCap)));
            basic.durabilityMode = DurabilityMode.parse(config.getOrElse("basic.durability_mode",
                    config.getOrElse("General.durabilityThreshold", basic.durabilityMode.name())), basic.durabilityMode);

            visual.showParticles = config.getOrElse("visual.show_particles",
                    config.getOrElse("Particles.enabled", visual.showParticles));
            visual.showChatFeedback = config.getOrElse("visual.show_chat_feedback", visual.showChatFeedback);
            visual.showHudIndicator = config.getOrElse("visual.show_hud_indicator", visual.showHudIndicator);
            particles.durationTicks = Math.max(1, config.getOrElse("visual.particle_duration_ticks",
                    config.getOrElse("Particles.durationTicks", particles.durationTicks)));
            particles.red = clampColor(config.getOrElse("visual.particle_red",
                    config.getOrElse("Particles.red", particles.red)), "visual.particle_red");
            particles.green = clampColor(config.getOrElse("visual.particle_green",
                    config.getOrElse("Particles.green", particles.green)), "visual.particle_green");
            particles.blue = clampColor(config.getOrElse("visual.particle_blue",
                    config.getOrElse("Particles.blue", particles.blue)), "visual.particle_blue");

            advanced.enabled = config.getOrElse("advanced.enabled", advanced.enabled);
            advanced.debugLogging = config.getOrElse("advanced.debug_logging", advanced.debugLogging);
            advanced.allowExperimentalFeatures = config.getOrElse("advanced.allow_experimental_features",
                    advanced.allowExperimentalFeatures);

            threadCount = Math.max(1, config.getOrElse("advanced.thread_count",
                    config.getOrElse("General.threadCount", threadCount)));

            cooldown.enabled = config.getOrElse("advanced.cooldown.enabled",
                    config.getOrElse("Cooldown.enabled", cooldown.enabled));
            cooldown.seconds = Math.max(0, config.getOrElse("advanced.cooldown.seconds",
                    config.getOrElse("Cooldown.seconds", cooldown.seconds)));

            blockLimits.dynamicMaxBlocks = config.getOrElse("advanced.block_limits.dynamic_max_blocks",
                    config.getOrElse("BlockLimits.dynamicMaxBlocks", blockLimits.dynamicMaxBlocks));
            blockLimits.maxBlocks = Math.max(1, config.getOrElse("advanced.block_limits.max_blocks",
                    config.getOrElse("BlockLimits.maxBlocks", blockLimits.maxBlocks)));
            blockLimits.minBlocks = Math.max(1, config.getOrElse("advanced.block_limits.min_blocks",
                    config.getOrElse("BlockLimits.minBlocks", blockLimits.minBlocks)));
            blockLimits.maxDynamicBlocks = Math.max(blockLimits.minBlocks,
                    config.getOrElse("advanced.block_limits.max_dynamic_blocks",
                            config.getOrElse("BlockLimits.maxDynamicBlocks", blockLimits.maxDynamicBlocks)));

            exhaustion.enabled = config.getOrElse("advanced.exhaustion.enabled",
                    config.getOrElse("Exhaustion.enabled", exhaustion.enabled));
            exhaustion.scale = clampNonNegative(readDouble(config, "advanced.exhaustion.scale",
                    readDouble(config, "Exhaustion.scale", exhaustion.scale)), "advanced.exhaustion.scale");

            boolean blocksPerTool = config.getOrElse("advanced.blocks_per_tool",
                    config.getOrElse("Advanced.blocksPerTool", blockListMode.perTool()));
            String modeRaw = config.getOrElse("advanced.block_list_mode", (String) null);
            if (modeRaw != null) {
                blockListMode = BlockListMode.parse(modeRaw, blocksPerTool, blockListMode);
            } else if (config.contains("Advanced.blockListMode")) {
                blockListMode = BlockListMode.parse(config.get("Advanced.blockListMode"), blocksPerTool, blockListMode);
            } else {
                blockListMode = BlockListMode.from(blocksPerTool, blockListMode.blacklist());
            }

            autoLuckPerms = config.getOrElse("advanced.auto_luckperms",
                    config.getOrElse("Integration.autoLuckPerms", autoLuckPerms));

            setupWizardPromptComplete = config.getOrElse("advanced.setup_wizard_prompt_complete",
                    config.getOrElse("Advanced.setupWizardPromptComplete", setupWizardPromptComplete));

            particles.enabled = visual.showParticles;

            if (!advanced.enabled) {
                forceBasicRuntimeMode();
            }

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

    private void forceBasicRuntimeMode() {
        // Keep non-essential features out of the default experience unless advanced mode is enabled.
        blockListMode = BlockListMode.GLOBAL_WHITELIST;
        cooldown.enabled = false;
        blockLimits.dynamicMaxBlocks = false;
        exhaustion.enabled = false;
        autoLuckPerms = false;
        threadCount = 2;
    }

    private void writeBack(CommentedFileConfig config) {
        config.set("basic.enabled", basic.enabled);
        config.setComment("basic.enabled", "Master Veinminer toggle.");

        config.set("basic.activation_key", basic.activationKey);
        config.setComment("basic.activation_key", "Default activation key. SHIFT means hold shift while mining.");

        config.set("basic.require_correct_tool", basic.requireCorrectTool);
        config.setComment("basic.require_correct_tool", "If true, only mine when the held tool can correctly harvest the block.");

        config.set("basic.check_tool_durability", basic.checkToolDurability);
        config.setComment("basic.check_tool_durability", "Prevent Veinminer from activating when the tool would break.");

        config.set("basic.durability_cap", basic.durabilityCap);
        config.setComment("basic.durability_cap", "Durability reserve threshold used when durability protection is enabled.");

        config.set("basic.durability_mode", basic.durabilityMode.name());
        config.setComment("basic.durability_mode", "ABSOLUTE keeps fixed points; PERCENTAGE keeps a percentage of max durability.");

        config.set("visual.show_particles", visual.showParticles);
        config.setComment("visual.show_particles", "Show vein outline particles.");

        config.set("visual.show_chat_feedback", visual.showChatFeedback);
        config.setComment("visual.show_chat_feedback", "Show Veinminer status/action messages in chat/action bar.");

        config.set("visual.show_hud_indicator", visual.showHudIndicator);
        config.setComment("visual.show_hud_indicator", "Reserved for a future HUD activation indicator.");

        config.set("visual.particle_duration_ticks", particles.durationTicks);
        config.set("visual.particle_red", particles.red);
        config.set("visual.particle_green", particles.green);
        config.set("visual.particle_blue", particles.blue);

        config.set("advanced.enabled", advanced.enabled);
        config.setComment("advanced.enabled", "Enable advanced commands (/vmadvanced) and advanced runtime controls.");

        config.set("advanced.debug_logging", advanced.debugLogging);
        config.setComment("advanced.debug_logging", "Enable extra debug-oriented logging.");

        config.set("advanced.allow_experimental_features", advanced.allowExperimentalFeatures);
        config.setComment("advanced.allow_experimental_features", "Gate for experimental behaviour.");

        config.set("advanced.thread_count", threadCount);
        config.setComment("advanced.thread_count", "Worker thread count for async internals.");

        config.set("advanced.block_list_mode", blockListMode.blacklist() ? "BLACKLIST" : "WHITELIST");
        config.setComment("advanced.block_list_mode", "Use WHITELIST or BLACKLIST semantics for block lists.");

        config.set("advanced.blocks_per_tool", blockListMode.perTool());
        config.setComment("advanced.blocks_per_tool", "If true, block lists are scoped per-tool.");

        config.set("advanced.auto_luckperms", autoLuckPerms);
        config.setComment("advanced.auto_luckperms", "Attempt automatic LuckPerms integration.");

        config.set("advanced.setup_wizard_prompt_complete", setupWizardPromptComplete);
        config.setComment("advanced.setup_wizard_prompt_complete", "Legacy setup wizard prompt state.");

        config.set("advanced.cooldown.enabled", cooldown.enabled);
        config.set("advanced.cooldown.seconds", cooldown.seconds);

        config.set("advanced.block_limits.dynamic_max_blocks", blockLimits.dynamicMaxBlocks);
        config.set("advanced.block_limits.max_blocks", blockLimits.maxBlocks);
        config.set("advanced.block_limits.min_blocks", blockLimits.minBlocks);
        config.set("advanced.block_limits.max_dynamic_blocks", blockLimits.maxDynamicBlocks);

        config.set("advanced.exhaustion.enabled", exhaustion.enabled);
        config.set("advanced.exhaustion.scale", exhaustion.scale);

        // Legacy keys removed once migrated to grouped sections.
        config.remove("General.veinminerEnabled");
        config.remove("General.requireCrouch");
        config.remove("General.checkToolDurability");
        config.remove("General.durabilityCap");
        config.remove("General.durabilityThreshold");
        config.remove("General.threadCount");
        config.remove("Cooldown.enabled");
        config.remove("Cooldown.seconds");
        config.remove("BlockLimits.dynamicMaxBlocks");
        config.remove("BlockLimits.maxBlocks");
        config.remove("BlockLimits.minBlocks");
        config.remove("BlockLimits.maxDynamicBlocks");
        config.remove("Particles.enabled");
        config.remove("Particles.durationTicks");
        config.remove("Particles.red");
        config.remove("Particles.green");
        config.remove("Particles.blue");
        config.remove("Exhaustion.enabled");
        config.remove("Exhaustion.scale");
        config.remove("Integration.autoLuckPerms");
        config.remove("Advanced.blockListMode");
        config.remove("Advanced.blocksPerTool");
        config.remove("Advanced.setupWizardPromptComplete");
    }

    private String normalizeActivationKey(String raw) {
        if (raw == null) {
            return "SHIFT";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "SHIFT";
        }
        return switch (normalized) {
            case "SHIFT", "SNEAK", "CROUCH" -> "SHIFT";
            case "ALWAYS", "NONE", "OFF" -> "ALWAYS";
            default -> "SHIFT";
        };
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

    public BasicSettings basic() {
        return basic;
    }

    public VisualSettings visual() {
        return visual;
    }

    public AdvancedSettings advanced() {
        return advanced;
    }

    public boolean veinminerEnabled() {
        return basic.enabled;
    }

    public void setVeinminerEnabled(boolean veinminerEnabled) {
        basic.enabled = veinminerEnabled;
    }

    public boolean requireCrouch() {
        return "SHIFT".equals(basic.activationKey);
    }

    public void setRequireCrouch(boolean requireCrouch) {
        basic.activationKey = requireCrouch ? "SHIFT" : "ALWAYS";
    }

    public boolean requireCorrectTool() {
        return basic.requireCorrectTool;
    }

    public void setRequireCorrectTool(boolean requireCorrectTool) {
        basic.requireCorrectTool = requireCorrectTool;
    }

    public boolean checkToolDurability() {
        return basic.checkToolDurability;
    }

    public void setCheckToolDurability(boolean checkToolDurability) {
        basic.checkToolDurability = checkToolDurability;
    }

    public int durabilityCap() {
        return basic.durabilityCap;
    }

    public void setDurabilityCap(int durabilityCap) {
        basic.durabilityCap = Math.max(0, durabilityCap);
    }

    public DurabilityMode durabilityMode() {
        return basic.durabilityMode;
    }

    public void setDurabilityMode(DurabilityMode durabilityMode) {
        basic.durabilityMode = Objects.requireNonNull(durabilityMode, "durabilityMode");
    }

    public int threadCount() {
        return Math.max(1, threadCount);
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
        if (basic.durabilityMode == DurabilityMode.PERCENTAGE) {
            double percentage = Math.max(0, Math.min(100, basic.durabilityCap));
            return (int) Math.ceil((percentage / 100.0) * maxDurability);
        }
        return Math.max(0, basic.durabilityCap);
    }

    public static final class BasicSettings {
        private boolean enabled = true;
        private String activationKey = "SHIFT";
        private boolean requireCorrectTool = true;
        private boolean checkToolDurability = true;
        private int durabilityCap = 1;
        private DurabilityMode durabilityMode = DurabilityMode.ABSOLUTE;

        public boolean enabled() {
            return enabled;
        }

        public String activationKey() {
            return activationKey;
        }

        public boolean requireCorrectTool() {
            return requireCorrectTool;
        }

        public boolean checkToolDurability() {
            return checkToolDurability;
        }

        public int durabilityCap() {
            return durabilityCap;
        }

        public DurabilityMode durabilityMode() {
            return durabilityMode;
        }
    }

    public static final class VisualSettings {
        private boolean showParticles = true;
        private boolean showChatFeedback = true;
        private boolean showHudIndicator = false;

        public boolean showParticles() {
            return showParticles;
        }

        public boolean showChatFeedback() {
            return showChatFeedback;
        }

        public boolean showHudIndicator() {
            return showHudIndicator;
        }
    }

    public static final class AdvancedSettings {
        private boolean enabled = false;
        private boolean debugLogging = false;
        private boolean allowExperimentalFeatures = false;

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean debugLogging() {
            return debugLogging;
        }

        public boolean allowExperimentalFeatures() {
            return allowExperimentalFeatures;
        }
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
