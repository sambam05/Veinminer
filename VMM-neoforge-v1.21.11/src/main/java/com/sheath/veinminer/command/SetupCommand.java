package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.config.GeneralConfig;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.util.Log;
import com.sheath.veinminer.util.Translations;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class SetupCommand {

    private static final int STEP_ENABLED = 1;
    private static final int STEP_REQUIRE_CROUCH = 2;
    private static final int STEP_BLOCK_LIMIT_MODE = 3;
    private static final int STEP_BLOCK_LIMIT_VALUES = 4;
    private static final int STEP_COOLDOWN = 5;
    private static final int STEP_EXHAUSTION = 6;
    private static final int STEP_DURABILITY_ENABLED = 7;
    private static final int STEP_DURABILITY_MODE = 8;
    private static final int STEP_DURABILITY_VALUE = 9;
    private static final int STEP_PARTICLES_ENABLED = 10;
    private static final int STEP_PARTICLE_DURATION = 11;
    private static final int STEP_PARTICLE_COLOR = 12;
    private static final int STEP_BLOCK_PER_TOOL = 13;
    private static final int STEP_BLOCK_LIST_MODE = 14;

    private static final int FINAL_STEP = STEP_BLOCK_LIST_MODE;
    private static final int CHAT_CLEAR_LINES = 40;

    private static final TextColor COLOR_HEADER = TextColor.fromRgb(0xFFFFFF);
    private static final TextColor COLOR_STEP = TextColor.fromRgb(0xCFCFCF);
    private static final TextColor COLOR_CURRENT_LABEL = TextColor.fromRgb(0x9CA3AF);
    private static final TextColor COLOR_CURRENT_VALUE = TextColor.fromRgb(0xF59E0B);
    private static final TextColor COLOR_DESCRIPTION = TextColor.fromRgb(0xD1D5DB);
    private static final TextColor COLOR_POSITIVE = TextColor.fromRgb(0x22C55E);
    private static final TextColor COLOR_NEGATIVE = TextColor.fromRgb(0x7A2E2E);
    private static final TextColor COLOR_NAV_BACK = TextColor.fromRgb(0x6B7280);
    private static final TextColor COLOR_NAV_NEXT = TextColor.fromRgb(0x22C55E);
    private static final TextColor COLOR_NAV_STATUS = TextColor.fromRgb(0x3B82F6);
    private static final TextColor COLOR_NAV_CANCEL = TextColor.fromRgb(0xDC2626);
    private static final TextColor COLOR_LINK = TextColor.fromRgb(0x3B82F6);

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private SetupCommand() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                        Predicate<CommandSourceStack> managePermission) {
        return Commands.literal("setup")
                .requires(managePermission::test)
                .executes(ctx -> showOrStart(ctx.getSource(), bootstrap))
                .then(Commands.literal("start").executes(ctx -> start(ctx.getSource(), bootstrap)))
                .then(Commands.literal("welcome")
                        .then(Commands.literal("yes").executes(ctx -> handleWelcomeChoice(ctx.getSource(), bootstrap, true)))
                        .then(Commands.literal("no").executes(ctx -> handleWelcomeChoice(ctx.getSource(), bootstrap, false))))
                .then(Commands.literal("show").executes(ctx -> show(ctx.getSource())))
                .then(Commands.literal("next").executes(ctx -> next(ctx.getSource())))
                .then(Commands.literal("back").executes(ctx -> back(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("apply").executes(ctx -> apply(ctx.getSource(), bootstrap)))
                .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setValue(
                                                ctx.getSource(),
                                                bootstrap,
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))));
    }

    private static int showOrStart(CommandSourceStack source, Bootstrap bootstrap) {
        Session session = getSession(source);
        if (session != null) {
            showStep(source, session);
            return 1;
        }
        return start(source, bootstrap);
    }

    private static int start(CommandSourceStack source, Bootstrap bootstrap) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        GeneralConfig general = bootstrap.configService().general();
        Session session = new Session(player.getUUID(), Draft.from(general));
        SESSIONS.put(player.getUUID(), session);
        showStep(source, session);
        return 1;
    }

    private static int handleWelcomeChoice(CommandSourceStack source, Bootstrap bootstrap, boolean runSetupNow) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        bootstrap.configService().general().setSetupWizardPromptComplete(true);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();

        if (runSetupNow) {
            return showOrStart(source, bootstrap);
        }

        player.displayClientMessage(Component.literal("No problem - you can run it later with ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/veinminer setup").withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static int show(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        showStep(source, session);
        return 1;
    }

    private static int next(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        session.step = normalizeForward(session, clampStep(session.step + 1));
        showStep(source, session);
        return 1;
    }

    private static int back(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        session.step = normalizeBackward(session, clampStep(session.step - 1));
        showStep(source, session);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        clearChat(source);
        source.sendSuccess(() -> buildSummaryText(session), false);
        sendLine(source, buildNavRow(session));
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        clearChat(source);
        SESSIONS.remove(player.getUUID());
        source.sendSuccess(() -> Component.literal("Veinminer setup cancelled."), false);
        return 1;
    }

    private static int apply(CommandSourceStack source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        clearChat(source);
        try {
            applyDraft(bootstrap, session.draft);
            SESSIONS.remove(session.playerId);
            source.sendSuccess(() -> Component.literal("Veinminer setup applied."), true);
            return 1;
        } catch (Exception ex) {
            Log.error("Failed to apply setup wizard config", ex);
            source.sendFailure(Component.literal("Failed to apply setup: " + safeMessage(ex)));
            return 0;
        }
    }

    private static int setValue(CommandSourceStack source,
                                Bootstrap bootstrap,
                                String key,
                                String rawValue) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        try {
            String normalizedKey = applyValue(session, key, rawValue);
            if (normalizedKey == null) {
                source.sendFailure(Component.literal("Unknown setup key '" + key + "'."));
                return 0;
            }
            adjustStepAfterValue(session, normalizedKey);
            showStep(source, session);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Invalid value: " + safeMessage(ex)));
            return 0;
        }
    }

    private static void applyDraft(Bootstrap bootstrap, Draft draft) {
        GeneralConfig general = bootstrap.configService().general();

        general.setVeinminerEnabled(draft.veinminerEnabled);
        general.setRequireCrouch(draft.requireCrouch);

        general.blockLimits().setDynamicMaxBlocks(draft.dynamicMaxBlocks);
        general.blockLimits().setMaxBlocks(draft.maxBlocks);
        general.blockLimits().setMinBlocks(draft.minBlocks);
        general.blockLimits().setMaxDynamicBlocks(draft.maxDynamicBlocks);

        general.cooldown().setEnabled(draft.cooldownEnabled);
        general.cooldown().setSeconds(draft.cooldownSeconds);

        general.exhaustion().setEnabled(draft.exhaustionEnabled);
        general.exhaustion().setScale(draft.exhaustionScale);

        general.setCheckToolDurability(draft.durabilityEnabled);
        general.setDurabilityMode(draft.durabilityMode);
        int durabilityCap = draft.durabilityCap;
        if (draft.durabilityMode == GeneralConfig.DurabilityMode.PERCENTAGE) {
            durabilityCap = Math.max(0, Math.min(100, durabilityCap));
        }
        general.setDurabilityCap(durabilityCap);

        general.particles().setEnabled(draft.particlesEnabled);
        general.particles().setDurationTicks(draft.particleDurationTicks);
        general.particles().setRed(draft.particleRed);
        general.particles().setGreen(draft.particleGreen);
        general.particles().setBlue(draft.particleBlue);

        general.setBlockListMode(draft.blockListMode);

        general.save();
        bootstrap.configService().loadAll();
        bootstrap.controller().reloadFromConfig();
    }

    private static void showStep(CommandSourceStack source, Session session) {
        session.step = normalizeForward(session, clampStep(session.step));
        clearChat(source);
        sendLine(source, colored("Setup Wizard (" + session.step + "/" + FINAL_STEP + ")", COLOR_HEADER, true));
        sendLine(source, Component.literal(""));

        switch (session.step) {
            case STEP_ENABLED -> showEnabledStep(source, session);
            case STEP_REQUIRE_CROUCH -> showRequireCrouchStep(source, session);
            case STEP_BLOCK_LIMIT_MODE -> showBlockLimitModeStep(source, session);
            case STEP_BLOCK_LIMIT_VALUES -> showBlockLimitValuesStep(source, session);
            case STEP_COOLDOWN -> showCooldownStep(source, session);
            case STEP_EXHAUSTION -> showExhaustionStep(source, session);
            case STEP_DURABILITY_ENABLED -> showDurabilityEnabledStep(source, session);
            case STEP_DURABILITY_MODE -> showDurabilityModeStep(source, session);
            case STEP_DURABILITY_VALUE -> showDurabilityValueStep(source, session);
            case STEP_PARTICLES_ENABLED -> showParticlesEnabledStep(source, session);
            case STEP_PARTICLE_DURATION -> showParticleDurationStep(source, session);
            case STEP_PARTICLE_COLOR -> showParticleColorStep(source, session);
            case STEP_BLOCK_PER_TOOL -> showBlockPerToolStep(source, session);
            case STEP_BLOCK_LIST_MODE -> showBlockListModeStep(source, session);
            default -> showSummaryStep(source, session);
        }

        sendLine(source, Component.literal(""));
        sendLine(source, buildNavRow(session));
    }

    private static int clampStep(int step) {
        return Math.max(1, Math.min(FINAL_STEP + 1, step));
    }

    private static int normalizeForward(Session session, int step) {
        if (!session.draft.durabilityEnabled && (step == STEP_DURABILITY_MODE || step == STEP_DURABILITY_VALUE)) {
            return STEP_PARTICLES_ENABLED;
        }
        if (!session.draft.particlesEnabled && (step == STEP_PARTICLE_DURATION || step == STEP_PARTICLE_COLOR)) {
            return STEP_BLOCK_PER_TOOL;
        }
        return step;
    }

    private static int normalizeBackward(Session session, int step) {
        if (!session.draft.particlesEnabled && step > STEP_PARTICLES_ENABLED && step <= STEP_PARTICLE_COLOR) {
            return STEP_PARTICLES_ENABLED;
        }
        if (!session.draft.durabilityEnabled && step > STEP_DURABILITY_ENABLED && step <= STEP_DURABILITY_VALUE) {
            return STEP_DURABILITY_ENABLED;
        }
        return step;
    }

    private static GeneralConfig.BlockListMode selectBlockListMode(boolean perTool, boolean blacklist) {
        if (perTool) {
            return blacklist ? GeneralConfig.BlockListMode.PER_TOOL_BLACKLIST : GeneralConfig.BlockListMode.PER_TOOL_WHITELIST;
        }
        return blacklist ? GeneralConfig.BlockListMode.GLOBAL_BLACKLIST : GeneralConfig.BlockListMode.GLOBAL_WHITELIST;
    }

    private static String describeBlockListMode(GeneralConfig.BlockListMode mode) {
        String type = mode.whitelist() ? "Whitelist" : "Blacklist";
        String scope = mode.perTool() ? "per-tool" : "global";
        return type + " (" + scope + ")";
    }

    private static void adjustStepAfterValue(Session session, String normalizedKey) {
        if (normalizedKey == null) {
            return;
        }
        switch (normalizedKey) {
            case "durabilityenabled", "checktooldurability" -> session.step = session.draft.durabilityEnabled
                    ? STEP_DURABILITY_MODE
                    : STEP_PARTICLES_ENABLED;
            case "particlesenabled" -> session.step = session.draft.particlesEnabled
                    ? STEP_PARTICLE_DURATION
                    : STEP_BLOCK_PER_TOOL;
            case "blockpertool", "blockspertool" -> session.step = STEP_BLOCK_LIST_MODE;
            default -> {
            }
        }
        session.step = normalizeForward(session, clampStep(session.step));
    }

    private static void clearChat(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            for (int i = 0; i < CHAT_CLEAR_LINES; i++) {
                player.sendSystemMessage(Component.literal(" "));
            }
        }
    }

    private static void showEnabledStep(CommandSourceStack source, Session session) {
        sendLine(source, colored("Step " + STEP_ENABLED + " - Enable Veinminer (Server-Wide):", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Toggle Veinminer for everyone on the server.",
                "Enable = Veinminer works; Disable = completely turn it off.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(session.draft.veinminerEnabled ? "Enabled" : "Disabled", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set enabled true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set enabled false", COLOR_NEGATIVE, false)));
    }

    private static void showRequireCrouchStep(CommandSourceStack source, Session session) {
        sendLine(source, colored("Step " + STEP_REQUIRE_CROUCH + " - Require crouch to activate:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Choose whether players must crouch (sneak) before vein mining starts.",
                "Enable = crouching required; Disable = vein mining can start while standing.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(session.draft.requireCrouch ? "True" : "False", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set requireCrouch true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set requireCrouch false", COLOR_NEGATIVE, false)));
    }

    private static void showBlockLimitModeStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_BLOCK_LIMIT_MODE + " - Block limits mode:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Choose how many blocks can be vein mined at once.",
                "Dynamic = scale with server TPS; Static = always use the fixed maxBlocks value.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored("Dynamic max blocks = " + draft.dynamicMaxBlocks, COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Dynamic ON", "/veinminer setup set dynamicMaxBlocks true", COLOR_POSITIVE, true),
                button("Dynamic OFF", "/veinminer setup set dynamicMaxBlocks false", COLOR_NEGATIVE, false)));
    }

    private static void showBlockLimitValuesStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_BLOCK_LIMIT_VALUES + " - Block limit values:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        if (draft.dynamicMaxBlocks) {
            sendDescription(source,
                    "Set the minimum and maximum blocks when dynamic scaling is enabled.",
                    "Veinminer moves between minBlocks and maxDynamicBlocks based on server TPS.");
            sendLine(source, Component.literal(""));
            sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                    .append(colored("Dynamic ON", COLOR_CURRENT_VALUE, true)));
            sendLine(source, colored("- minBlocks (dynamic): " + draft.minBlocks, COLOR_CURRENT_LABEL, false));
            sendLine(source, colored("- maxDynamicBlocks (dynamic): " + draft.maxDynamicBlocks, COLOR_CURRENT_LABEL, false));
            sendLine(source, row(
                suggestButton("Set minBlocks...", "/veinminer setup set minBlocks "),
                suggestButton("Set maxDynamic...", "/veinminer setup set maxDynamicBlocks ")));
        } else {
            sendDescription(source,
                    "Use a single static limit for all vein mines.",
                    "maxBlocks = maximum blocks broken in one use.");
            sendLine(source, Component.literal(""));
            sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                    .append(colored("Dynamic OFF", COLOR_CURRENT_VALUE, true)));
            sendLine(source, colored("- maxBlocks (static): " + draft.maxBlocks, COLOR_CURRENT_LABEL, false));
            sendLine(source, row(
                suggestButton("Set maxBlocks...", "/veinminer setup set maxBlocks ")));
        }
    }

    private static void showCooldownStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_COOLDOWN + " - Cooldown:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Add a delay between vein mining uses.",
                "Enable = enforce the cooldown seconds; Disable = no cooldown.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(draft.cooldownEnabled ? "Enabled" : "Disabled", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set cooldownEnabled true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set cooldownEnabled false", COLOR_NEGATIVE, false)));
        if (draft.cooldownEnabled) {
            sendLine(source, colored("Seconds: " + draft.cooldownSeconds, COLOR_CURRENT_LABEL, false));
            sendLine(source, row(
                    suggestButton("Set seconds...", "/veinminer setup set cooldownSeconds ")));
        }
    }

    private static void showExhaustionStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_EXHAUSTION + " - Hunger exhaustion:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Decide whether vein mining adds hunger exhaustion.",
                "Enable = apply exhaustion (set a scale); Disable = no extra hunger drain.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(draft.exhaustionEnabled ? "Enabled" : "Disabled", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set exhaustionEnabled true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set exhaustionEnabled false", COLOR_NEGATIVE, false)));
        if (draft.exhaustionEnabled) {
            sendLine(source, colored("Scale: " + draft.exhaustionScale + " (1.0 = vanilla)", COLOR_CURRENT_LABEL, false));
            sendLine(source, row(
                    button("Less", "/veinminer setup set exhaustionScale 0.5", COLOR_LINK, false),
                    button("Vanilla", "/veinminer setup set exhaustionScale 1.0", COLOR_LINK, false),
                    button("More", "/veinminer setup set exhaustionScale 2.0", COLOR_LINK, false),
                    suggestButton("Custom...", "/veinminer setup set exhaustionScale ")));
        }
    }

    private static void showDurabilityEnabledStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_DURABILITY_ENABLED + " - Durability guard:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Protect tools from breaking by reserving durability.",
                "Enable = stop vein mining when the guard threshold is reached; Disable = ignore tool durability.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(draft.durabilityEnabled ? "Enabled" : "Disabled", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set durabilityEnabled true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set durabilityEnabled false", COLOR_NEGATIVE, false)));
    }

    private static void showDurabilityModeStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        if (!draft.durabilityEnabled) {
            session.step = STEP_PARTICLES_ENABLED;
            showStep(source, session);
            return;
        }
        sendLine(source, colored("Step " + STEP_DURABILITY_MODE + " - Durability guard mode:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Pick how the durability guard threshold is interpreted.",
                "ABSOLUTE = keep a fixed number of durability points; PERCENTAGE = keep a percent of the tool.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(String.valueOf(draft.durabilityMode), COLOR_CURRENT_VALUE, true)));
        sendLine(source, colored("Value: " + draft.durabilityCap + (draft.durabilityMode == GeneralConfig.DurabilityMode.PERCENTAGE ? "%" : " durability"), COLOR_CURRENT_LABEL, false));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("ABSOLUTE", "/veinminer setup set durabilityMode ABSOLUTE", COLOR_LINK, false),
                button("PERCENTAGE", "/veinminer setup set durabilityMode PERCENTAGE", COLOR_LINK, false)));
    }

    private static void showDurabilityValueStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        if (!draft.durabilityEnabled) {
            session.step = STEP_PARTICLES_ENABLED;
            showStep(source, session);
            return;
        }
        sendLine(source, colored("Step " + STEP_DURABILITY_VALUE + " - Durability guard value:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Set the threshold that stops vein mining.",
                "Matches the selected mode: raw durability for ABSOLUTE or a percent for PERCENTAGE.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: " + draft.durabilityCap + (draft.durabilityMode == GeneralConfig.DurabilityMode.PERCENTAGE ? "%" : " durability"), COLOR_CURRENT_LABEL, false));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                suggestButton("Set value...", "/veinminer setup set durabilityCap ")));
    }

    private static void showParticlesEnabledStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_PARTICLES_ENABLED + " - Particle outline:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Show particles around blocks queued for vein mining.",
                "Enable = draw an outline; Disable = no outline visuals.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(draft.particlesEnabled ? "Enabled" : "Disabled", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set particlesEnabled true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set particlesEnabled false", COLOR_NEGATIVE, false)));
    }

    private static void showParticleDurationStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        if (!draft.particlesEnabled) {
            session.step = STEP_BLOCK_PER_TOOL;
            showStep(source, session);
            return;
        }
        sendLine(source, colored("Step " + STEP_PARTICLE_DURATION + " - Particle duration:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Control how long the outline particles stay visible.",
                "Higher tick values = outline lasts longer after triggering vein mining.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: " + draft.particleDurationTicks + " ticks", COLOR_CURRENT_LABEL, false));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                suggestButton("Set duration...", "/veinminer setup set particleDurationTicks ")));
    }

    private static void showParticleColorStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        if (!draft.particlesEnabled) {
            session.step = STEP_BLOCK_PER_TOOL;
            showStep(source, session);
            return;
        }
        sendLine(source, colored("Step " + STEP_PARTICLE_COLOR + " - Particle color (RGB 0-255):", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Choose the outline color using red, green, and blue channels (0-255 each).",
                "Adjust any channel to tint the highlight; reset values to tweak brightness.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: " + draft.particleRed + ", " + draft.particleGreen + ", " + draft.particleBlue, COLOR_CURRENT_LABEL, false));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                suggestButton("Set red...", "/veinminer setup set particleRed "),
                suggestButton("Set green...", "/veinminer setup set particleGreen "),
                suggestButton("Set blue...", "/veinminer setup set particleBlue ")));
    }

    private static void showBlockPerToolStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_BLOCK_PER_TOOL + " - Block list scope:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Choose between one global block list or separate lists per tool.",
                "Enable = per-tool block lists; Disable = single global block list.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(draft.blockListMode.perTool() ? "Per-tool" : "Global", COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Enable", "/veinminer setup set blockPerTool true", COLOR_POSITIVE, true),
                button("Disable", "/veinminer setup set blockPerTool false", COLOR_NEGATIVE, false)));
    }

    private static void showBlockListModeStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        sendLine(source, colored("Step " + STEP_BLOCK_LIST_MODE + " - Block list mode:", COLOR_STEP, false));
        sendLine(source, Component.literal(""));
        sendDescription(source,
                "Set whether the chosen block list acts as a whitelist or blacklist.",
                "Whitelist = only listed blocks are vein mined; Blacklist = everything except listed blocks.");
        sendLine(source, Component.literal(""));
        sendLine(source, colored("Current: ", COLOR_CURRENT_LABEL, false)
                .append(colored(describeBlockListMode(draft.blockListMode), COLOR_CURRENT_VALUE, true)));
        sendLine(source, Component.literal(""));
        sendLine(source, row(
                button("Whitelist", "/veinminer setup set blockListMode " + selectBlockListMode(draft.blockListMode.perTool(), false).name(), COLOR_POSITIVE, true),
                button("Blacklist", "/veinminer setup set blockListMode " + selectBlockListMode(draft.blockListMode.perTool(), true).name(), COLOR_NEGATIVE, true)));
    }

    private static void showSummaryStep(CommandSourceStack source, Session session) {
        source.sendSuccess(() -> buildSummaryText(session), false);
        sendLine(source, row(
                button("Apply", "/veinminer setup apply").withStyle(ChatFormatting.GREEN),
                button("Cancel", "/veinminer setup cancel").withStyle(ChatFormatting.RED)));
    }

    private static Component buildSummaryText(Session session) {
        Draft draft = session.draft;
        return Component.literal("")
                .append(Component.literal("Draft settings:\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("- veinminerEnabled: " + draft.veinminerEnabled + "\n"))
                .append(Component.literal("- requireCrouch: " + draft.requireCrouch + "\n"))
                .append(Component.literal("- block lists: " + describeBlockListMode(draft.blockListMode) + "\n"))
                .append(Component.literal("- cooldown: " + (draft.cooldownEnabled ? ("on (" + draft.cooldownSeconds + "s)") : "off") + "\n"))
                .append(Component.literal("- exhaustion: " + (draft.exhaustionEnabled ? ("on (x" + draft.exhaustionScale + ")") : "off") + "\n"))
                .append(Component.literal("- durability guard: " + (draft.durabilityEnabled ? ("on (" + draft.durabilityMode + "=" + draft.durabilityCap + ")") : "off") + "\n"))
                .append(Component.literal("- particles: " + (draft.particlesEnabled ? ("on (" + draft.particleDurationTicks + "t, " + draft.particleRed + "," + draft.particleGreen + "," + draft.particleBlue + ")") : "off") + "\n"))
                .append(Component.literal("- limits: dynamic=" + draft.dynamicMaxBlocks + ", max=" + draft.maxBlocks + ", min=" + draft.minBlocks + ", maxDynamic=" + draft.maxDynamicBlocks + "\n"))
                .append(Component.literal("\n"))
                .append(Component.literal("Tip: click buttons, or use /veinminer setup set <key> <value>.").withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent buildNavRow(Session session) {
        MutableComponent row = Component.literal("");
        row.append(button("Back", "/veinminer setup back", COLOR_NAV_BACK, false));
        row.append(Component.literal(" "));
        row.append(button("Next", "/veinminer setup next", COLOR_NAV_NEXT, true));
        row.append(Component.literal(" "));
        row.append(button("Status", "/veinminer setup status", COLOR_NAV_STATUS, false));
        row.append(Component.literal(" "));
        row.append(button("Cancel", "/veinminer setup cancel", COLOR_NAV_CANCEL, true));
        return row;
    }

    private static MutableComponent row(MutableComponent... buttons) {
        MutableComponent row = Component.literal("");
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) row.append(Component.literal(" "));
            row.append(buttons[i]);
        }
        return row;
    }

    private static void sendLine(CommandSourceStack source, Component text) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(text);
        } else {
            source.sendSystemMessage(text);
        }
    }

    private static void sendDescription(CommandSourceStack source, String... lines) {
        for (String line : lines) {
            sendLine(source, colored(line, COLOR_DESCRIPTION, false));
        }
    }

    private static MutableComponent colored(String text, TextColor color, boolean bold) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withBold(bold));
    }

    private static MutableComponent button(String label, String command) {
        return button(label, command, COLOR_LINK, false);
    }

    private static MutableComponent button(String label, String command, TextColor color, boolean bold) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> {
                    var result = style.withColor(color).withBold(bold);
                    ClickEvent click = createClickEvent(ClickEvent.Action.RUN_COMMAND, command);
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command));
                    if (hover != null) {
                        result = result.withHoverEvent(hover);
                    }
                    return result;
                });
    }

    private static MutableComponent suggestButton(String label, String suggestion) {
        return suggestButton(label, suggestion, COLOR_LINK, false);
    }

    private static MutableComponent suggestButton(String label, String suggestion, TextColor color, boolean bold) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> {
                    var result = style.withColor(color).withBold(bold);
                    ClickEvent click = createClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion);
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(suggestion));
                    if (hover != null) {
                        result = result.withHoverEvent(hover);
                    }
                    return result;
                });
    }

    private static ClickEvent createClickEvent(ClickEvent.Action action, String value) {
        for (var ctor : ClickEvent.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1] == String.class) {
                try {
                    ctor.setAccessible(true);
                    return (ClickEvent) ctor.newInstance(action, value);
                } catch (Throwable ignored) {
                }
            }
        }

        for (var method : action.getClass().getDeclaredMethods()) {
            if (ClickEvent.class.isAssignableFrom(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                    try {
                        method.setAccessible(true);
                        return (ClickEvent) method.invoke(action, value);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        for (var method : ClickEvent.class.getDeclaredMethods()) {
            if (ClickEvent.class.isAssignableFrom(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                try {
                    if (params.length == 1 && params[0] == String.class) {
                        method.setAccessible(true);
                        return (ClickEvent) method.invoke(null, value);
                    }
                    if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1] == String.class) {
                        method.setAccessible(true);
                        return (ClickEvent) method.invoke(null, action, value);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static HoverEvent createHoverEvent(HoverEvent.Action action, Component text) {
        for (var ctor : HoverEvent.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1].isAssignableFrom(Component.class)) {
                try {
                    ctor.setAccessible(true);
                    return (HoverEvent) ctor.newInstance(action, text);
                } catch (Throwable ignored) {
                }
            }
        }

        for (var method : action.getClass().getDeclaredMethods()) {
            if (HoverEvent.class.isAssignableFrom(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(Component.class)) {
                    try {
                        method.setAccessible(true);
                        return (HoverEvent) method.invoke(action, text);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        for (var method : HoverEvent.class.getDeclaredMethods()) {
            if (HoverEvent.class.isAssignableFrom(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                try {
                    if (params.length == 1 && params[0].isAssignableFrom(Component.class)) {
                        method.setAccessible(true);
                        return (HoverEvent) method.invoke(null, text);
                    }
                    if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1].isAssignableFrom(Component.class)) {
                        method.setAccessible(true);
                        return (HoverEvent) method.invoke(null, action, text);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static String applyValue(Session session, String rawKey, String rawValue) {
        Draft draft = session.draft;
        String key = rawKey.trim().toLowerCase(java.util.Locale.ROOT);
        String value = rawValue.trim();
        switch (key) {
            case "enabled", "veinminerenabled" -> draft.veinminerEnabled = parseBoolean(value);
            case "requirecrouch" -> draft.requireCrouch = parseBoolean(value);
            case "cooldownenabled" -> draft.cooldownEnabled = parseBoolean(value);
            case "cooldownseconds" -> draft.cooldownSeconds = parseInt(value, 0, Integer.MAX_VALUE);
            case "exhaustionenabled" -> draft.exhaustionEnabled = parseBoolean(value);
            case "exhaustionscale" -> {
                draft.exhaustionScale = parseDouble(value, 0.0, Double.MAX_VALUE);
                draft.exhaustionEnabled = true;
            }
            case "durabilityenabled", "checktooldurability" -> draft.durabilityEnabled = parseBoolean(value);
            case "durabilitymode" -> draft.durabilityMode = parseDurabilityMode(value, draft.durabilityMode);
            case "durabilitycap", "durabilityvalue" -> draft.durabilityCap = parseInt(value, 0, Integer.MAX_VALUE);
            case "dynamicmaxblocks" -> draft.dynamicMaxBlocks = parseBoolean(value);
            case "maxblocks" -> draft.maxBlocks = parseInt(value, 1, Integer.MAX_VALUE);
            case "minblocks" -> draft.minBlocks = parseInt(value, 1, Integer.MAX_VALUE);
            case "maxdynamicblocks" -> draft.maxDynamicBlocks = parseInt(value, 1, Integer.MAX_VALUE);
            case "particlesenabled" -> draft.particlesEnabled = parseBoolean(value);
            case "particleduration", "particledurationticks" -> draft.particleDurationTicks = parseInt(value, 1, Integer.MAX_VALUE);
            case "particlered" -> draft.particleRed = parseInt(value, 0, 255);
            case "particlegreen" -> draft.particleGreen = parseInt(value, 0, 255);
            case "particleblue" -> draft.particleBlue = parseInt(value, 0, 255);
            case "blockpertool", "blockspertool" -> draft.blockListMode = selectBlockListMode(parseBoolean(value), draft.blockListMode.blacklist());
            case "blocklistmode" -> draft.blockListMode = GeneralConfig.BlockListMode.parse(value, draft.blockListMode.perTool(), draft.blockListMode);
            default -> {
                return null;
            }
        }
        return key;
    }

    private static GeneralConfig.DurabilityMode parseDurabilityMode(String raw, GeneralConfig.DurabilityMode fallback) {
        if (raw == null) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return GeneralConfig.DurabilityMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw) {
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "true", "t", "yes", "y", "1", "on", "enable", "enabled" -> true;
            case "false", "f", "no", "n", "0", "off", "disable", "disabled" -> false;
            default -> throw new IllegalArgumentException("Expected boolean, got '" + raw + "'");
        };
    }

    private static int parseInt(String raw, int min, int max) {
        int value = Integer.parseInt(raw.trim());
        if (value < min || value > max) {
            throw new IllegalArgumentException("Expected " + min + "-" + max + ", got " + value);
        }
        return value;
    }

    private static double parseDouble(String raw, double min, double max) {
        double value = Double.parseDouble(raw.trim());
        if (value < min || value > max) {
            throw new IllegalArgumentException("Expected " + min + "-" + max + ", got " + value);
        }
        return value;
    }

    private static Session getSession(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return null;
        }
        return SESSIONS.get(player.getUUID());
    }

    private static Session requireSession(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return null;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            source.sendFailure(Component.literal("No setup session found. Run /veinminer setup start."));
        }
        return session;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return player;
        }
        source.sendFailure(Translations.translate("command.veinminer.player_only"));
        return null;
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private static final class Session {
        private final UUID playerId;
        private int step = STEP_ENABLED;
        private final Draft draft;

        private Session(UUID playerId, Draft draft) {
            this.playerId = playerId;
            this.draft = draft;
        }
    }

    private static final class Draft {
        private boolean veinminerEnabled;
        private boolean requireCrouch;

        private boolean dynamicMaxBlocks;
        private int maxBlocks;
        private int minBlocks;
        private int maxDynamicBlocks;

        private boolean cooldownEnabled;
        private int cooldownSeconds;

        private boolean exhaustionEnabled;
        private double exhaustionScale;

        private boolean durabilityEnabled;
        private GeneralConfig.DurabilityMode durabilityMode;
        private int durabilityCap;

        private boolean particlesEnabled;
        private int particleDurationTicks;
        private int particleRed;
        private int particleGreen;
        private int particleBlue;

        private GeneralConfig.BlockListMode blockListMode;

        private static Draft from(GeneralConfig general) {
            Draft draft = new Draft();
            draft.veinminerEnabled = general.veinminerEnabled();
            draft.requireCrouch = general.requireCrouch();

            draft.dynamicMaxBlocks = general.blockLimits().dynamicMaxBlocks();
            draft.maxBlocks = general.blockLimits().maxBlocks();
            draft.minBlocks = general.blockLimits().minBlocks();
            draft.maxDynamicBlocks = general.blockLimits().maxDynamicBlocks();

            draft.cooldownEnabled = general.cooldown().enabled();
            draft.cooldownSeconds = general.cooldown().seconds();

            draft.exhaustionEnabled = general.exhaustion().enabled();
            draft.exhaustionScale = general.exhaustion().scale();

            draft.durabilityEnabled = general.checkToolDurability();
            draft.durabilityMode = general.durabilityMode();
            draft.durabilityCap = general.durabilityCap();

            draft.particlesEnabled = general.particles().enabled();
            draft.particleDurationTicks = general.particles().durationTicks();
            draft.particleRed = general.particles().red();
            draft.particleGreen = general.particles().green();
            draft.particleBlue = general.particles().blue();

            draft.blockListMode = general.blockListMode();
            return draft;
        }
    }
}





