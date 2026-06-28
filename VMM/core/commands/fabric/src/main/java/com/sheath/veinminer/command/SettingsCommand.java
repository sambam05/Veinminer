package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.config.GeneralConfig;
import com.sheath.veinminer.core.Bootstrap;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import com.sheath.veinminer.util.Translations;

import java.util.Locale;
import java.util.function.Predicate;

final class SettingsCommand {

    private SettingsCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> buildAdmin(Bootstrap bootstrap,
                                                               Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("settings")
                .requires(managePermission::test)
                .executes(ctx -> showAdminSettings(ctx.getSource(), bootstrap))
                .then(CommandManager.literal("blocklistmode")
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("whitelist");
                                    builder.suggest("blacklist");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setBlockListMode(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        StringArgumentType.getString(ctx, "mode")))))
                .then(CommandManager.literal("maxblocks")
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> setMaxBlocks(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(buildCooldownNode(bootstrap, managePermission))
                .then(buildExhaustionNode(bootstrap, managePermission))
                .then(buildLuckPermsNode(bootstrap, managePermission));
    }

    static ArgumentBuilder<ServerCommandSource, ?> buildAdvanced(Bootstrap bootstrap,
                                                                  Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("settings")
                .requires(managePermission::test)
                .executes(ctx -> showSettings(ctx.getSource(), bootstrap))
                .then(CommandManager.literal("blockpertool").executes(ctx -> toggleBlockPerTool(ctx.getSource(), bootstrap, managePermission)));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> buildCooldownNode(Bootstrap bootstrap,
                                                                             Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("cooldown")
                .then(CommandManager.literal("enable")
                        .executes(ctx -> setCooldownEnabled(ctx.getSource(), bootstrap, managePermission, true)))
                .then(CommandManager.literal("disable")
                        .executes(ctx -> setCooldownEnabled(ctx.getSource(), bootstrap, managePermission, false)))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(ctx -> setCooldownSeconds(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> buildLuckPermsNode(Bootstrap bootstrap,
                                                                              Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("luckperms")
                .then(CommandManager.literal("enable")
                        .executes(ctx -> setLuckPerms(ctx.getSource(), bootstrap, managePermission, true)))
                .then(CommandManager.literal("disable")
                        .executes(ctx -> setLuckPerms(ctx.getSource(), bootstrap, managePermission, false)));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> buildExhaustionNode(Bootstrap bootstrap,
                                                                              Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("exhaustion")
                .then(CommandManager.literal("enable")
                        .executes(ctx -> setExhaustionEnabled(ctx.getSource(), bootstrap, managePermission, true)))
                .then(CommandManager.literal("disable")
                        .executes(ctx -> setExhaustionEnabled(ctx.getSource(), bootstrap, managePermission, false)))
                .then(CommandManager.literal("scale")
                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0))
                                .executes(ctx -> setExhaustionScale(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        DoubleArgumentType.getDouble(ctx, "value")))));
    }

    private static int toggleBlockPerTool(ServerCommandSource source,
                                          Bootstrap bootstrap,
                                          Predicate<ServerCommandSource> managePermission) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }

        GeneralConfig general = bootstrap.configService().general();
        GeneralConfig.BlockListMode current = general.blockListMode();
        GeneralConfig.BlockListMode next = selectMode(!current.perTool(), current.blacklist());
        general.setBlockListMode(next);
        general.save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();

        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.blockpertool",
                Translations.translate(next.perTool()
                        ? "command.veinminer.enabled"
                        : "command.veinminer.disabled"),
                describeListType(next)), true);
        return 1;
    }

    private static int setBlockListMode(ServerCommandSource source,
                                        Bootstrap bootstrap,
                                        Predicate<ServerCommandSource> managePermission,
                                        String rawMode) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        Boolean blacklist = switch (normalized) {
            case "whitelist" -> false;
            case "blacklist" -> true;
            default -> null;
        };
        if (blacklist == null) {
            source.sendError(Translations.translate("command.veinminer.settings.blocklistmode_invalid", rawMode));
            return 0;
        }
        GeneralConfig general = bootstrap.configService().general();
        GeneralConfig.BlockListMode mode = selectMode(general.blockListMode().perTool(), blacklist);
        general.setBlockListMode(mode);
        general.save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.blocklistmode_set",
                describeMode(mode)), true);
        return 1;
    }

    private static int setCooldownSeconds(ServerCommandSource source,
                                          Bootstrap bootstrap,
                                          Predicate<ServerCommandSource> managePermission,
                                          int seconds) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().cooldown().setSeconds(seconds);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.settings.cooldown_time_set", seconds), true);
        return 1;
    }

    private static int setCooldownEnabled(ServerCommandSource source,
                                          Bootstrap bootstrap,
                                          Predicate<ServerCommandSource> managePermission,
                                          boolean enabled) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().cooldown().setEnabled(enabled);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.cooldown_state",
                Translations.translate(enabled ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        return 1;
    }

    private static int setLuckPerms(ServerCommandSource source,
                                    Bootstrap bootstrap,
                                    Predicate<ServerCommandSource> managePermission,
                                    boolean enabled) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().setAutoLuckPerms(enabled);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.luckperms_state",
                Translations.translate(enabled ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        return 1;
    }

    private static int setExhaustionEnabled(ServerCommandSource source,
                                           Bootstrap bootstrap,
                                           Predicate<ServerCommandSource> managePermission,
                                           boolean enabled) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().exhaustion().setEnabled(enabled);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.exhaustion_state",
                Translations.translate(enabled ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        return 1;
    }

    private static int setExhaustionScale(ServerCommandSource source,
                                         Bootstrap bootstrap,
                                         Predicate<ServerCommandSource> managePermission,
                                         double scale) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().exhaustion().setScale(scale);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.settings.exhaustion_scale_set", scale), true);
        return 1;
    }

    private static GeneralConfig.BlockListMode selectMode(boolean perTool, boolean blacklist) {
        if (perTool) {
            return blacklist
                    ? GeneralConfig.BlockListMode.PER_TOOL_BLACKLIST
                    : GeneralConfig.BlockListMode.PER_TOOL_WHITELIST;
        }
        return blacklist
                ? GeneralConfig.BlockListMode.GLOBAL_BLACKLIST
                : GeneralConfig.BlockListMode.GLOBAL_WHITELIST;
    }

    private static Text describeMode(GeneralConfig.BlockListMode mode) {
        String key = switch (mode) {
            case GLOBAL_WHITELIST -> "command.veinminer.block_list_type.global_whitelist";
            case PER_TOOL_WHITELIST -> "command.veinminer.block_list_type.per_tool_whitelist";
            case GLOBAL_BLACKLIST -> "command.veinminer.block_list_type.global_blacklist";
            case PER_TOOL_BLACKLIST -> "command.veinminer.block_list_type.per_tool_blacklist";
        };
        return Translations.translate(key);
    }

    private static Text describeListType(GeneralConfig.BlockListMode mode) {
        String key = mode.blacklist()
                ? "command.veinminer.block_list_type.blacklist"
                : "command.veinminer.block_list_type.whitelist";
        return Translations.translate(key);
    }

    private static int setMaxBlocks(ServerCommandSource source,
                                    Bootstrap bootstrap,
                                    Predicate<ServerCommandSource> managePermission,
                                    int maxBlocks) {
        if (!managePermission.test(source)) {
            source.sendError(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().blockLimits().setMaxBlocks(maxBlocks);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.settings.max_blocks_set", maxBlocks), true);
        return 1;
    }

    private static int showSettings(ServerCommandSource source, Bootstrap bootstrap) {
        var snapshot = bootstrap.configService().snapshot();
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.block_list_mode",
                describeMode(snapshot.general().blockListMode())), false);
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.max_blocks",
                snapshot.general().blockLimits().maxBlocks()), false);
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.cooldown_enabled",
                snapshot.general().cooldown().enabled()), false);
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.cooldown_time",
                snapshot.general().cooldown().seconds()), false);
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.exhaustion_enabled",
                snapshot.general().exhaustion().enabled()), false);
        source.sendFeedback(() -> Translations.translate(
                "command.veinminer.settings.show.exhaustion_scale",
                snapshot.general().exhaustion().scale()), false);
        return 1;
    }

    private static int showAdminSettings(ServerCommandSource source, Bootstrap bootstrap) {
        return showSettings(source, bootstrap);
    }

}
