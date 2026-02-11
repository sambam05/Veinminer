package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.config.GeneralConfig;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.permission.PermissionCompat;
import com.sheath.veinminer.state.ClearConfirmationManager;
import com.sheath.veinminer.util.Log;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

final class BlocksCommand {

    private BlocksCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                         Predicate<ServerCommandSource> managePermission,
                                                         ClearConfirmationManager confirmations) {
        return CommandManager.literal("blocks")
                .requires(managePermission::test)
                .then(CommandManager.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> list(ctx.getSource(), bootstrap))
                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestRegistryBlocks())
                                .executes(ctx -> add(ctx.getSource(), bootstrap,
                                        StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredBlocks(bootstrap))
                                .executes(ctx -> remove(ctx.getSource(), bootstrap,
                                        StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClear(ctx.getSource(), bootstrap, confirmations)));
    }

    private static SuggestionProvider<ServerCommandSource> suggestRegistryBlocks() {
        return (ctx, builder) -> suggestBlocksAndTags(builder);
    }

    private static SuggestionProvider<ServerCommandSource> suggestConfiguredBlocks(Bootstrap bootstrap) {
        return (ctx, builder) -> {
            List<String> suggestions = new ArrayList<>(bootstrap.configService().snapshot().allowedBlocks().raw());
            if (suggestions.isEmpty()) {
                return builder.buildFuture();
            }
            return CommandSource.suggestMatching(suggestions, builder);
        };
    }

    private static int requestClear(ServerCommandSource source,
                                    Bootstrap bootstrap,
                                    ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, bootstrap, VeinMinerController.Permissions.BLOCKS_MANAGE)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text descriptor = describeGlobalList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearAllowedBlocks();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blocks.cleared", descriptor), true);
            } else {
                confirmSource.sendError(Translations.translate("command.veinminer.blocks.none", descriptor));
            }
        });
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBlocksAndTags(SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>(Registries.BLOCK.getIds().size());
        for (Identifier id : Registries.BLOCK.getIds()) {
            suggestions.add(id.toString());
        }
        Registries.BLOCK.streamTags().forEach(tag -> {
            String tagId = tagId(tag);
            if (tagId != null) {
                suggestions.add("#" + tagId);
            }
        });
        return CommandSource.suggestMatching(suggestions, builder);
    }

    private static int list(ServerCommandSource source, Bootstrap bootstrap) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKS_LIST,
                VeinMinerController.Permissions.BLOCKS_MANAGE)) {
            return 0;
        }
        ConfigService.ConfigSnapshot snapshot = bootstrap.configService().snapshot();
        List<String> entries = List.copyOf(snapshot.allowedBlocks().raw());
        GeneralConfig.BlockListMode mode = snapshot.general().blockListMode();
        Text descriptor = describeGlobalList(mode);

        if (entries.isEmpty()) {
            source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.none", descriptor), false);
            if (mode.perTool()) {
                source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.per_tool_hint"), false);
            }
            return 1;
        }

        source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.header", descriptor), false);
        if (mode.perTool()) {
            source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.per_tool_hint"), false);
        }
        entries.forEach(entry ->
                source.sendFeedback(() -> Translations.translate("command.veinminer.list_entry", entry), false));
        return 1;
    }

    private static int add(ServerCommandSource source,
                           Bootstrap bootstrap,
                           String rawEntry) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKS_ADD,
                VeinMinerController.Permissions.BLOCKS_MANAGE)) {
            return 0;
        }

        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        ConfigService.ChangeResult result = bootstrap.configService().addAllowedBlock(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.added",
                        rawEntry,
                        describeGlobalList(mode)), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendError(Translations.translate("command.veinminer.blocks.exists",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            case INVALID -> {
                source.sendError(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when adding block {}", result, rawEntry);
                source.sendError(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
        };
    }

    private static int remove(ServerCommandSource source,
                              Bootstrap bootstrap,
                              String rawEntry) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKS_REMOVE,
                VeinMinerController.Permissions.BLOCKS_MANAGE)) {
            return 0;
        }

        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        ConfigService.ChangeResult result = bootstrap.configService().removeAllowedBlock(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate("command.veinminer.blocks.removed",
                        rawEntry,
                        describeGlobalList(mode)), true);
                yield 1;
            }
            case NOT_FOUND -> {
                source.sendError(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            case INVALID -> {
                source.sendError(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when removing block {}", result, rawEntry);
                source.sendError(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
        };
    }

    private static boolean ensurePermission(ServerCommandSource source,
                                            Bootstrap bootstrap,
                                            String... nodes) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return PermissionCompat.hasPermissionLevel(source, 2);
        }
        for (String node : nodes) {
            if (bootstrap.permissionService().hasPermission(player, node)) {
                return true;
            }
            int dot = node.lastIndexOf('.');
            if (dot > 0) {
                String wildcard = node.substring(0, dot) + ".*";
                if (bootstrap.permissionService().hasPermission(player, wildcard)) {
                    return true;
                }
            }
        }
        if (bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)) {
            return true;
        }
        if (PermissionCompat.hasPermissionLevel(source, 2)) {
            return true;
        }
        source.sendError(Translations.translate("message.veinminer.no_permission"));
        return false;
    }

    private static Text describeGlobalList(GeneralConfig.BlockListMode mode) {
        String key = mode.blacklist()
                ? "command.veinminer.block_list_type.global_blacklist"
                : "command.veinminer.block_list_type.global_whitelist";
        return Translations.translate(key);
    }

    private static String tagId(Object tag) {
        if (tag instanceof TagKey<?> tagKey) {
            return tagKey.id().toString();
        }
        try {
            Method id = tag.getClass().getMethod("id");
            Object value = id.invoke(tag);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }
        try {
            Method getTag = tag.getClass().getMethod("getTag");
            Object value = getTag.invoke(tag);
            if (value instanceof TagKey<?> tagKey) {
                return tagKey.id().toString();
            }
        } catch (Exception ignored) {
        }
        try {
            Method getTagKey = tag.getClass().getMethod("getTagKey");
            Object value = getTagKey.invoke(tag);
            if (value instanceof TagKey<?> tagKey) {
                return tagKey.id().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Translations.translate("command.veinminer.help.blocks"), false);
        return 1;
    }
}
