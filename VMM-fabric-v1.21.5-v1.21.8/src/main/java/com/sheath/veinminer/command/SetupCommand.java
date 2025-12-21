package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.config.GeneralConfig;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.util.Log;
import com.sheath.veinminer.util.Translations;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class SetupCommand {

    private static final int FINAL_STEP = 9;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private SetupCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                         Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("setup")
                .requires(managePermission::test)
                .executes(ctx -> showOrStart(ctx.getSource(), bootstrap))
                .then(CommandManager.literal("start").executes(ctx -> start(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("welcome")
                        .then(CommandManager.literal("yes").executes(ctx -> handleWelcomeChoice(ctx.getSource(), bootstrap, true)))
                        .then(CommandManager.literal("no").executes(ctx -> handleWelcomeChoice(ctx.getSource(), bootstrap, false))))
                .then(CommandManager.literal("show").executes(ctx -> show(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("next").executes(ctx -> next(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("back").executes(ctx -> back(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("status").executes(ctx -> status(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("apply").executes(ctx -> apply(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("key", StringArgumentType.word())
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setValue(
                                                ctx.getSource(),
                                                bootstrap,
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))));
    }

    private static int showOrStart(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = getSession(source);
        if (session != null) {
            showStep(source, session);
            return 1;
        }
        return start(source, bootstrap);
    }

    private static int start(ServerCommandSource source, Bootstrap bootstrap) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        GeneralConfig general = bootstrap.configService().general();
        Session session = new Session(player.getUuid(), Draft.from(general));
        SESSIONS.put(player.getUuid(), session);
        showStep(source, session);
        return 1;
    }

    private static int handleWelcomeChoice(ServerCommandSource source, Bootstrap bootstrap, boolean runSetupNow) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        bootstrap.configService().general().setSetupWizardPromptComplete(true);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();

        if (runSetupNow) {
            return showOrStart(source, bootstrap);
        }

        player.sendMessage(Text.literal("No problem - you can run it later with ").formatted(Formatting.GRAY)
                .append(Text.literal("/veinminer setup").formatted(Formatting.AQUA)), false);
        return 1;
    }

    private static int show(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        showStep(source, session);
        return 1;
    }

    private static int next(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        session.step = Math.min(FINAL_STEP + 1, session.step + 1);
        showStep(source, session);
        return 1;
    }

    private static int back(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        session.step = Math.max(1, session.step - 1);
        showStep(source, session);
        return 1;
    }

    private static int status(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        source.sendFeedback(() -> buildSummaryText(session), false);
        return 1;
    }

    private static int cancel(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        SESSIONS.remove(player.getUuid());
        source.sendFeedback(() -> Text.literal("Veinminer setup cancelled."), false);
        return 1;
    }

    private static int apply(ServerCommandSource source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        try {
            applyDraft(bootstrap, session.draft);
            SESSIONS.remove(session.playerId);
            source.sendFeedback(() -> Text.literal("Veinminer setup applied."), true);
            return 1;
        } catch (Exception ex) {
            Log.error("Failed to apply setup wizard config", ex);
            source.sendError(Text.literal("Failed to apply setup: " + safeMessage(ex)));
            return 0;
        }
    }

    private static int setValue(ServerCommandSource source,
                                Bootstrap bootstrap,
                                String key,
                                String rawValue) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        try {
            if (!applyValue(session.draft, key, rawValue)) {
                source.sendError(Text.literal("Unknown setup key '" + key + "'."));
                return 0;
            }
            showStep(source, session);
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("Invalid value: " + safeMessage(ex)));
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

    private static void showStep(ServerCommandSource source, Session session) {
        source.sendFeedback(() -> Text.literal(""), false);
        if (session.step >= 1 && session.step <= FINAL_STEP) {
            source.sendFeedback(() -> Text.literal("Setup Wizard (" + session.step + "/" + FINAL_STEP + ")"), false);
        } else {
            source.sendFeedback(() -> Text.literal("Setup Wizard (Summary)"), false);
        }

        switch (session.step) {
            case 1 -> showEnabledStep(source, session);
            case 2 -> showRequireCrouchStep(source, session);
            case 3 -> showBlockLimitModeStep(source, session);
            case 4 -> showBlockLimitValuesStep(source, session);
            case 5 -> showCooldownStep(source, session);
            case 6 -> showExhaustionStep(source, session);
            case 7 -> showDurabilityStep(source, session);
            case 8 -> showParticlesStep(source, session);
            case 9 -> showBlockListModeStep(source, session);
            default -> showSummaryStep(source, session);
        }

        sendLine(source, buildNavRow(session));
    }

    private static void showEnabledStep(ServerCommandSource source, Session session) {
        source.sendFeedback(() -> Text.literal("Step 1 ??? Enable Veinminer (server-wide):"), false);
        source.sendFeedback(() -> Text.literal("Current: " + (session.draft.veinminerEnabled ? "enabled" : "disabled")), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set enabled true"),
                button("Disable", "/veinminer setup set enabled false")));
    }

    private static void showRequireCrouchStep(ServerCommandSource source, Session session) {
        source.sendFeedback(() -> Text.literal("Step 2 ??? Require crouch to activate:"), false);
        source.sendFeedback(() -> Text.literal("Current: " + (session.draft.requireCrouch ? "true" : "false")), false);
        sendLine(source, row(
                button("True", "/veinminer setup set requireCrouch true"),
                button("False", "/veinminer setup set requireCrouch false")));
    }

    private static void showBlockLimitModeStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 3 ??? Block limits mode:"), false);
        source.sendFeedback(() -> Text.literal("Dynamic TPS-aware max blocks: " + draft.dynamicMaxBlocks), false);
        sendLine(source, row(
                button("Dynamic ON", "/veinminer setup set dynamicMaxBlocks true"),
                button("Dynamic OFF", "/veinminer setup set dynamicMaxBlocks false")));
    }

    private static void showBlockLimitValuesStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 4 ??? Block limit values:"), false);
        if (draft.dynamicMaxBlocks) {
            source.sendFeedback(() -> Text.literal("Dynamic limits are ON"), false);
            source.sendFeedback(() -> Text.literal("- minBlocks (dynamic): " + draft.minBlocks), false);
            source.sendFeedback(() -> Text.literal("- maxDynamicBlocks (dynamic): " + draft.maxDynamicBlocks), false);
            sendLine(source, row(
                    suggestButton("Set minBlocks???", "/veinminer setup set minBlocks "),
                    suggestButton("Set maxDynamic???", "/veinminer setup set maxDynamicBlocks ")));
        } else {
            source.sendFeedback(() -> Text.literal("Dynamic limits are OFF"), false);
            source.sendFeedback(() -> Text.literal("- maxBlocks (static): " + draft.maxBlocks), false);
            sendLine(source, row(
                    suggestButton("Set maxBlocks???", "/veinminer setup set maxBlocks ")));
        }
    }

    private static void showCooldownStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 5 ??? Cooldown:"), false);
        source.sendFeedback(() -> Text.literal("Enabled: " + draft.cooldownEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set cooldownEnabled true"),
                button("Disable", "/veinminer setup set cooldownEnabled false")));
        if (draft.cooldownEnabled) {
            source.sendFeedback(() -> Text.literal("Seconds: " + draft.cooldownSeconds), false);
            sendLine(source, row(
                    suggestButton("Set seconds???", "/veinminer setup set cooldownSeconds ")));
        }
    }

    private static void showExhaustionStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 6 ??? Hunger exhaustion:"), false);
        source.sendFeedback(() -> Text.literal("Enabled: " + draft.exhaustionEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set exhaustionEnabled true"),
                button("Disable", "/veinminer setup set exhaustionEnabled false")));
        if (draft.exhaustionEnabled) {
            source.sendFeedback(() -> Text.literal("Scale: " + draft.exhaustionScale + " (1.0 = vanilla)"), false);
            sendLine(source, row(
                    button("Less", "/veinminer setup set exhaustionScale 0.5"),
                    button("Vanilla", "/veinminer setup set exhaustionScale 1.0"),
                    button("More", "/veinminer setup set exhaustionScale 2.0"),
                    suggestButton("Custom scale???", "/veinminer setup set exhaustionScale ")));
        }
    }

    private static void showDurabilityStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 7 ??? Durability guard:"), false);
        source.sendFeedback(() -> Text.literal("Enabled: " + draft.durabilityEnabled), false);
        sendLine(source, row(
                button("On", "/veinminer setup set durabilityEnabled true"),
                button("Off", "/veinminer setup set durabilityEnabled false")));
        if (draft.durabilityEnabled) {
            source.sendFeedback(() -> Text.literal("Mode: " + draft.durabilityMode), false);
            source.sendFeedback(() -> Text.literal("Value: " + draft.durabilityCap + (draft.durabilityMode == GeneralConfig.DurabilityMode.PERCENTAGE ? "%" : " durability")), false);
            sendLine(source, row(
                    button("ABSOLUTE", "/veinminer setup set durabilityMode ABSOLUTE"),
                    button("PERCENTAGE", "/veinminer setup set durabilityMode PERCENTAGE"),
                    suggestButton("Set value???", "/veinminer setup set durabilityCap ")));
        }
    }

    private static void showParticlesStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 8 ??? Particle outline:"), false);
        source.sendFeedback(() -> Text.literal("Enabled: " + draft.particlesEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set particlesEnabled true"),
                button("Disable", "/veinminer setup set particlesEnabled false")));
        if (draft.particlesEnabled) {
            source.sendFeedback(() -> Text.literal("Duration: " + draft.particleDurationTicks + " ticks"), false);
            source.sendFeedback(() -> Text.literal("Color: " + draft.particleRed + ", " + draft.particleGreen + ", " + draft.particleBlue), false);
            sendLine(source, row(
                    suggestButton("Set duration???", "/veinminer setup set particleDurationTicks ")));
            sendLine(source, row(
                    suggestButton("Set red???", "/veinminer setup set particleRed "),
                    suggestButton("Set green???", "/veinminer setup set particleGreen "),
                    suggestButton("Set blue???", "/veinminer setup set particleBlue ")));
        }
    }

    private static void showBlockListModeStep(ServerCommandSource source, Session session) {
        Draft draft = session.draft;
        source.sendFeedback(() -> Text.literal("Step 9 ??? Block list mode:"), false);
        source.sendFeedback(() -> Text.literal("Current: " + draft.blockListMode), false);
        sendLine(source, row(
                button("Global Whitelist", "/veinminer setup set blockListMode GLOBAL_WHITELIST"),
                button("Global Blacklist", "/veinminer setup set blockListMode GLOBAL_BLACKLIST")));
        sendLine(source, row(
                button("Per-tool Whitelist", "/veinminer setup set blockListMode PER_TOOL_WHITELIST"),
                button("Per-tool Blacklist", "/veinminer setup set blockListMode PER_TOOL_BLACKLIST")));
    }

    private static void showSummaryStep(ServerCommandSource source, Session session) {
        source.sendFeedback(() -> buildSummaryText(session), false);
        sendLine(source, row(
                button("Apply", "/veinminer setup apply").formatted(Formatting.GREEN),
                button("Cancel", "/veinminer setup cancel").formatted(Formatting.RED)));
    }

    private static Text buildSummaryText(Session session) {
        Draft draft = session.draft;
        return Text.literal("")
                .append(Text.literal("Draft settings:\n").formatted(Formatting.GOLD))
                .append(Text.literal("- veinminerEnabled: " + draft.veinminerEnabled + "\n"))
                .append(Text.literal("- requireCrouch: " + draft.requireCrouch + "\n"))
                .append(Text.literal("- blockListMode: " + draft.blockListMode + "\n"))
                .append(Text.literal("- cooldown: " + (draft.cooldownEnabled ? ("on (" + draft.cooldownSeconds + "s)") : "off") + "\n"))
                .append(Text.literal("- exhaustion: " + (draft.exhaustionEnabled ? ("on (x" + draft.exhaustionScale + ")") : "off") + "\n"))
                .append(Text.literal("- durability guard: " + (draft.durabilityEnabled ? ("on (" + draft.durabilityMode + "=" + draft.durabilityCap + ")") : "off") + "\n"))
                .append(Text.literal("- particles: " + (draft.particlesEnabled ? ("on (" + draft.particleDurationTicks + "t, " + draft.particleRed + "," + draft.particleGreen + "," + draft.particleBlue + ")") : "off") + "\n"))
                .append(Text.literal("- limits: dynamic=" + draft.dynamicMaxBlocks + ", max=" + draft.maxBlocks + ", min=" + draft.minBlocks + ", maxDynamic=" + draft.maxDynamicBlocks + "\n"))
                .append(Text.literal("\n"))
                .append(Text.literal("Tip: click buttons, or use /veinminer setup set <key> <value>.").formatted(Formatting.GRAY));
    }

    private static MutableText buildNavRow(Session session) {
        MutableText row = Text.literal("");
        row.append(button("Back", "/veinminer setup back"));
        row.append(Text.literal(" "));
        row.append(button("Next", "/veinminer setup next"));
        row.append(Text.literal(" "));
        row.append(button("Status", "/veinminer setup status"));
        row.append(Text.literal(" "));
        row.append(button("Cancel", "/veinminer setup cancel").formatted(Formatting.RED));
        return row;
    }

    private static MutableText row(MutableText... buttons) {
        MutableText row = Text.literal("");
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) row.append(Text.literal(" "));
            row.append(buttons[i]);
        }
        return row;
    }

    private static void sendLine(ServerCommandSource source, Text text) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            player.sendMessage(text, false);
        } else {
            source.sendFeedback(() -> text, false);
        }
    }

    private static MutableText button(String label, String command) {
        return Text.literal("[" + label + "]")
                .styled(style -> {
                    var result = style.withColor(Formatting.AQUA);
                    ClickEvent click = createClickEvent(ClickEvent.Action.RUN_COMMAND, command);
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command));
                    if (hover != null) {
                        result = result.withHoverEvent(hover);
                    }
                    return result;
                });
    }

    private static MutableText suggestButton(String label, String suggestion) {
        return Text.literal("[" + label + "]")
                .styled(style -> {
                    var result = style.withColor(Formatting.YELLOW);
                    ClickEvent click = createClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion);
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(suggestion));
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

    private static HoverEvent createHoverEvent(HoverEvent.Action action, Text text) {
        for (var ctor : HoverEvent.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1].isAssignableFrom(Text.class)) {
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
                if (params.length == 1 && params[0].isAssignableFrom(Text.class)) {
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
                    if (params.length == 1 && params[0].isAssignableFrom(Text.class)) {
                        method.setAccessible(true);
                        return (HoverEvent) method.invoke(null, text);
                    }
                    if (params.length == 2 && params[0].isAssignableFrom(action.getClass()) && params[1].isAssignableFrom(Text.class)) {
                        method.setAccessible(true);
                        return (HoverEvent) method.invoke(null, action, text);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }
    private static boolean applyValue(Draft draft, String rawKey, String rawValue) {
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
            case "blocklistmode" -> draft.blockListMode = GeneralConfig.BlockListMode.parse(value, draft.blockListMode);
            default -> {
                return false;
            }
        }
        return true;
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

    private static Session getSession(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return null;
        }
        return SESSIONS.get(player.getUuid());
    }

    private static Session requireSession(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return null;
        }
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            source.sendError(Text.literal("No setup session found. Run /veinminer setup start."));
        }
        return session;
    }

    private static ServerPlayerEntity requirePlayer(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }
        source.sendError(Translations.translate("command.veinminer.player_only"));
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
        private int step = 1;
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





