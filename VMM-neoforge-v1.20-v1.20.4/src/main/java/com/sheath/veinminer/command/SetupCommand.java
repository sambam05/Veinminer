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
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class SetupCommand {

    private static final int FINAL_STEP = 9;

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
        session.step = Math.min(FINAL_STEP + 1, session.step + 1);
        showStep(source, session);
        return 1;
    }

    private static int back(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        session.step = Math.max(1, session.step - 1);
        showStep(source, session);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
        source.sendSuccess(() -> buildSummaryText(session), false);
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        SESSIONS.remove(player.getUUID());
        source.sendSuccess(() -> Component.literal("Veinminer setup cancelled."), false);
        return 1;
    }

    private static int apply(CommandSourceStack source, Bootstrap bootstrap) {
        Session session = requireSession(source);
        if (session == null) {
            return 0;
        }
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
            if (!applyValue(session.draft, key, rawValue)) {
                source.sendFailure(Component.literal("Unknown setup key '" + key + "'."));
                return 0;
            }
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
        source.sendSuccess(() -> Component.literal(""), false);
        if (session.step >= 1 && session.step <= FINAL_STEP) {
            source.sendSuccess(() -> Component.literal("Setup Wizard (" + session.step + "/" + FINAL_STEP + ")"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Setup Wizard (Summary)"), false);
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

    private static void showEnabledStep(CommandSourceStack source, Session session) {
        source.sendSuccess(() -> Component.literal("Step 1 ??? Enable Veinminer (server-wide):"), false);
        source.sendSuccess(() -> Component.literal("Current: " + (session.draft.veinminerEnabled ? "enabled" : "disabled")), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set enabled true"),
                button("Disable", "/veinminer setup set enabled false")));
    }

    private static void showRequireCrouchStep(CommandSourceStack source, Session session) {
        source.sendSuccess(() -> Component.literal("Step 2 ??? Require crouch to activate:"), false);
        source.sendSuccess(() -> Component.literal("Current: " + (session.draft.requireCrouch ? "true" : "false")), false);
        sendLine(source, row(
                button("True", "/veinminer setup set requireCrouch true"),
                button("False", "/veinminer setup set requireCrouch false")));
    }

    private static void showBlockLimitModeStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 3 ??? Block limits mode:"), false);
        source.sendSuccess(() -> Component.literal("Dynamic TPS-aware max blocks: " + draft.dynamicMaxBlocks), false);
        sendLine(source, row(
                button("Dynamic ON", "/veinminer setup set dynamicMaxBlocks true"),
                button("Dynamic OFF", "/veinminer setup set dynamicMaxBlocks false")));
    }

    private static void showBlockLimitValuesStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 4 ??? Block limit values:"), false);
        if (draft.dynamicMaxBlocks) {
            source.sendSuccess(() -> Component.literal("Dynamic limits are ON"), false);
            source.sendSuccess(() -> Component.literal("- minBlocks (dynamic): " + draft.minBlocks), false);
            source.sendSuccess(() -> Component.literal("- maxDynamicBlocks (dynamic): " + draft.maxDynamicBlocks), false);
            sendLine(source, row(
                suggestButton("Set minBlocks???", "/veinminer setup set minBlocks "),
                suggestButton("Set maxDynamic???", "/veinminer setup set maxDynamicBlocks ")));
        } else {
            source.sendSuccess(() -> Component.literal("Dynamic limits are OFF"), false);
            source.sendSuccess(() -> Component.literal("- maxBlocks (static): " + draft.maxBlocks), false);
            sendLine(source, row(
                suggestButton("Set maxBlocks???", "/veinminer setup set maxBlocks ")));
        }
    }

    private static void showCooldownStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 5 ??? Cooldown:"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + draft.cooldownEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set cooldownEnabled true"),
                button("Disable", "/veinminer setup set cooldownEnabled false")));
        if (draft.cooldownEnabled) {
            source.sendSuccess(() -> Component.literal("Seconds: " + draft.cooldownSeconds), false);
            sendLine(source, row(
                    suggestButton("Set seconds???", "/veinminer setup set cooldownSeconds ")));
        }
    }

    private static void showExhaustionStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 6 ??? Hunger exhaustion:"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + draft.exhaustionEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set exhaustionEnabled true"),
                button("Disable", "/veinminer setup set exhaustionEnabled false")));
        if (draft.exhaustionEnabled) {
            source.sendSuccess(() -> Component.literal("Scale: " + draft.exhaustionScale + " (1.0 = vanilla)"), false);
            sendLine(source, row(
                    button("Less", "/veinminer setup set exhaustionScale 0.5"),
                    button("Vanilla", "/veinminer setup set exhaustionScale 1.0"),
                    button("More", "/veinminer setup set exhaustionScale 2.0"),
                    suggestButton("Custom scale???", "/veinminer setup set exhaustionScale ")));
        }
    }

    private static void showDurabilityStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 7 ??? Durability guard:"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + draft.durabilityEnabled), false);
        sendLine(source, row(
                button("On", "/veinminer setup set durabilityEnabled true"),
                button("Off", "/veinminer setup set durabilityEnabled false")));
        if (draft.durabilityEnabled) {
            source.sendSuccess(() -> Component.literal("Mode: " + draft.durabilityMode), false);
            source.sendSuccess(() -> Component.literal("Value: " + draft.durabilityCap + (draft.durabilityMode == GeneralConfig.DurabilityMode.PERCENTAGE ? "%" : " durability")), false);
            sendLine(source, row(
                    button("ABSOLUTE", "/veinminer setup set durabilityMode ABSOLUTE"),
                    button("PERCENTAGE", "/veinminer setup set durabilityMode PERCENTAGE"),
                    suggestButton("Set value???", "/veinminer setup set durabilityCap ")));
        }
    }

    private static void showParticlesStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 8 ??? Particle outline:"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + draft.particlesEnabled), false);
        sendLine(source, row(
                button("Enable", "/veinminer setup set particlesEnabled true"),
                button("Disable", "/veinminer setup set particlesEnabled false")));
        if (draft.particlesEnabled) {
            source.sendSuccess(() -> Component.literal("Duration: " + draft.particleDurationTicks + " ticks"), false);
            source.sendSuccess(() -> Component.literal("Color: " + draft.particleRed + ", " + draft.particleGreen + ", " + draft.particleBlue), false);
            sendLine(source, row(
                    suggestButton("Set duration???", "/veinminer setup set particleDurationTicks ")));
            sendLine(source, row(
                    suggestButton("Set red???", "/veinminer setup set particleRed "),
                    suggestButton("Set green???", "/veinminer setup set particleGreen "),
                    suggestButton("Set blue???", "/veinminer setup set particleBlue ")));
        }
    }

    private static void showBlockListModeStep(CommandSourceStack source, Session session) {
        Draft draft = session.draft;
        source.sendSuccess(() -> Component.literal("Step 9 ??? Block list mode:"), false);
        source.sendSuccess(() -> Component.literal("Current: " + draft.blockListMode), false);
        sendLine(source, row(
                button("Global Whitelist", "/veinminer setup set blockListMode GLOBAL_WHITELIST"),
                button("Global Blacklist", "/veinminer setup set blockListMode GLOBAL_BLACKLIST")));
        sendLine(source, row(
                button("Per-tool Whitelist", "/veinminer setup set blockListMode PER_TOOL_WHITELIST"),
                button("Per-tool Blacklist", "/veinminer setup set blockListMode PER_TOOL_BLACKLIST")));
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
                .append(Component.literal("- blockListMode: " + draft.blockListMode + "\n"))
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
        row.append(button("Back", "/veinminer setup back"));
        row.append(Component.literal(" "));
        row.append(button("Next", "/veinminer setup next"));
        row.append(Component.literal(" "));
        row.append(button("Status", "/veinminer setup status"));
        row.append(Component.literal(" "));
        row.append(button("Cancel", "/veinminer setup cancel").withStyle(ChatFormatting.RED));
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

    private static MutableComponent button(String label, String command) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> {
                    var result = style.withColor(ChatFormatting.AQUA);
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
        return Component.literal("[" + label + "]")
                .withStyle(style -> {
                    var result = style.withColor(ChatFormatting.YELLOW);
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





