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
import java.util.NavigableSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

final class BlockPerToolCommand {

    private BlockPerToolCommand() {}

    static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                         Predicate<ServerCommandSource> managePermission,
                                                         ClearConfirmationManager confirmations) {
        return CommandManager.literal("blockpertool")
                .requires(managePermission::test)
                .then(buildBlocksNode(bootstrap, managePermission, confirmations))
                .then(buildToolNode(bootstrap, managePermission, confirmations));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> buildBlocksNode(Bootstrap bootstrap,
                                                                           Predicate<ServerCommandSource> managePermission,
                                                                           ClearConfirmationManager confirmations) {
        return CommandManager.literal("blocks")
                .then(CommandManager.argument("tool", StringArgumentType.string())
                        .suggests(suggestConfiguredTools(bootstrap))
                        .then(CommandManager.literal("add")
                                .requires(managePermission::test)
                                .then(CommandManager.argument("block", StringArgumentType.greedyString())
                                        .suggests(suggestAllBlocksOrTags())
                                        .executes(ctx -> addBlock(
                                                ctx.getSource(),
                                                bootstrap,
                                                StringArgumentType.getString(ctx, "tool"),
                                                StringArgumentType.getString(ctx, "block")))))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> listBlocks(
                                        ctx.getSource(),
                                        bootstrap,
                                        StringArgumentType.getString(ctx, "tool"))))
                        .then(CommandManager.literal("remove")
                                .requires(managePermission::test)
                                .then(CommandManager.argument("block", StringArgumentType.greedyString())
                                        .suggests(suggestBlocksForTool(bootstrap))
                                        .executes(ctx -> removeBlock(
                                                ctx.getSource(),
                                                bootstrap,
                                        StringArgumentType.getString(ctx, "tool"),
                                        StringArgumentType.getString(ctx, "block")))))
                        .then(CommandManager.literal("clear")
                                .requires(managePermission::test)
                                .executes(ctx -> requestClearBlocks(
                                        ctx.getSource(),
                                        bootstrap,
                                        confirmations,
                                        StringArgumentType.getString(ctx, "tool")))));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> buildToolNode(Bootstrap bootstrap,
                                                                         Predicate<ServerCommandSource> managePermission,
                                                                         ClearConfirmationManager confirmations) {
        return CommandManager.literal("tool")
                .then(CommandManager.literal("add")
                        .requires(managePermission::test)
                        .then(CommandManager.argument("tool", StringArgumentType.greedyString())
                                .suggests(suggestAllTools())
                                        .executes(ctx -> addTool(
                                                ctx.getSource(),
                                                bootstrap,
                                                StringArgumentType.getString(ctx, "tool")))))
                .then(CommandManager.literal("list")
                        .executes(ctx -> listTools(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("remove")
                        .requires(managePermission::test)
                        .then(CommandManager.argument("tool", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredTools(bootstrap))
                                .executes(ctx -> removeTool(
                                        ctx.getSource(),
                                        bootstrap,
                                        StringArgumentType.getString(ctx, "tool")))))
                .then(CommandManager.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClearAllTools(ctx.getSource(), bootstrap, confirmations)));
    }

    private static int addBlock(ServerCommandSource source,
                                Bootstrap bootstrap,
                                String tool,
                                String block) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_BLOCKS_ADD,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text listDescriptor = describePerToolList(mode);

        ConfigService.ChangeResult result = bootstrap.configService().addBlocksPerToolBlock(tool, block);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.block_added", block, listDescriptor, tool), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.block_exists", block, listDescriptor));
                yield 0;
            }
            case INVALID, NOT_FOUND -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
            default -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
        };
    }

    private static int requestClearBlocks(ServerCommandSource source,
                                          Bootstrap bootstrap,
                                          ClearConfirmationManager confirmations,
                                          String tool) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        NavigableSet<String> blocks = bootstrap.configService().getBlocksForTool(tool);
        if (blocks == null) {
            source.sendError(Translations.translate(
                    "command.veinminer.blockpertool.tool_not_found", tool));
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text descriptor = describePerToolList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearBlocksForTool(tool);
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.blocks_cleared", descriptor, tool), true);
            } else {
                confirmSource.sendError(Translations.translate(
                        "command.veinminer.blockpertool.no_blocks", descriptor, tool));
            }
        });
        return 1;
    }

    private static int requestClearAllTools(ServerCommandSource source,
                                            Bootstrap bootstrap,
                                            ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text descriptor = describePerToolList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearBlocksPerTool();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.tools_cleared", descriptor), true);
            } else {
                confirmSource.sendError(Translations.translate("command.veinminer.blockpertool.no_tools"));
            }
        });
        return 1;
    }

    private static int removeBlock(ServerCommandSource source,
                                   Bootstrap bootstrap,
                                   String tool,
                                   String block) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_BLOCKS_REMOVE,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text listDescriptor = describePerToolList(mode);

        ConfigService.ChangeResult result = bootstrap.configService().removeBlocksPerToolBlock(tool, block);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.block_removed", block, listDescriptor, tool), true);
                yield 1;
            }
            case NOT_FOUND, INVALID, ALREADY_PRESENT -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
        };
    }

    private static int listBlocks(ServerCommandSource source,
                                  Bootstrap bootstrap,
                                  String tool) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_BLOCKS_LIST,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Text listDescriptor = describePerToolList(mode);
        NavigableSet<String> blocks = bootstrap.configService().getBlocksForTool(tool);
        if (blocks == null || blocks.isEmpty()) {
            source.sendFeedback(() -> Translations.translate(
                    "command.veinminer.blockpertool.no_blocks", listDescriptor, tool), false);
        } else {
            source.sendFeedback(() -> Translations.translate(
                    "command.veinminer.blockpertool.blocks_for_tool", listDescriptor, tool), false);
            blocks.forEach(block ->
                    source.sendFeedback(() -> Translations.translate("command.veinminer.list_entry", block), false));
        }
        return 1;
    }

    private static int addTool(ServerCommandSource source,
                               Bootstrap bootstrap,
                               String tool) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_TOOLS_ADD,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        ConfigService.ChangeResult result = bootstrap.configService().addBlocksPerToolTool(tool);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.tool_added", tool), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.tool_exists", tool));
                yield 0;
            }
            case INVALID, NOT_FOUND -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.tool_not_found", tool));
                yield 0;
            }
        };
    }

    private static int removeTool(ServerCommandSource source,
                                  Bootstrap bootstrap,
                                  String tool) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_TOOLS_REMOVE,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        ConfigService.ChangeResult result = bootstrap.configService().removeBlocksPerToolTool(tool);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate(
                        "command.veinminer.blockpertool.tool_removed", tool), true);
                yield 1;
            }
            case NOT_FOUND, INVALID, ALREADY_PRESENT -> {
                source.sendError(Translations.translate(
                        "command.veinminer.blockpertool.tool_not_found", tool));
                yield 0;
            }
        };
    }

    private static int listTools(ServerCommandSource source, Bootstrap bootstrap) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.BLOCKPERTOOL_TOOLS_LIST,
                VeinMinerController.Permissions.BLOCKPERTOOL_MANAGE) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        NavigableSet<String> tools = bootstrap.configService().getAllToolKeys();
        if (tools == null || tools.isEmpty()) {
            source.sendFeedback(() -> Translations.translate("command.veinminer.blockpertool.no_tools"), false);
        } else {
            source.sendFeedback(() -> Translations.translate("command.veinminer.blockpertool.tools_header"), false);
            tools.forEach(tool ->
                    source.sendFeedback(() -> Translations.translate("command.veinminer.list_entry", tool), false));
        }
        return 1;
    }

    private static SuggestionProvider<ServerCommandSource> suggestConfiguredTools(Bootstrap bootstrap) {
        return (ctx, builder) -> suggestValues(bootstrap.configService().getAllToolKeys(), builder);
    }

    private static SuggestionProvider<ServerCommandSource> suggestAllTools() {
        return (ctx, builder) -> CommandSource.suggestMatching(collectToolOptions(), builder);
    }

    private static SuggestionProvider<ServerCommandSource> suggestAllBlocksOrTags() {
        return (ctx, builder) -> suggestBlocksAndTags(builder);
    }

    private static SuggestionProvider<ServerCommandSource> suggestBlocksForTool(Bootstrap bootstrap) {
        return (ctx, builder) -> {
            String tool = "";
            try {
                tool = StringArgumentType.getString(ctx, "tool");
            } catch (IllegalArgumentException ignored) {
            }
            NavigableSet<String> entries = bootstrap.configService().getBlocksForTool(tool);
            if (entries == null || entries.isEmpty()) {
                return suggestBlocksAndTags(builder);
            }
            return CommandSource.suggestMatching(entries, builder);
        };
    }

    private static CompletableFuture<Suggestions> suggestValues(Iterable<String> values, SuggestionsBuilder builder) {
        if (values == null) {
            return builder.buildFuture();
        }
        List<String> options = new ArrayList<>();
        for (String value : values) {
            // Only suggest quoted form so colon/tag ids insert cleanly as one argument
            options.add("\"" + value + "\"");
        }
        if (options.isEmpty()) {
            return builder.buildFuture();
        }
        return CommandSource.suggestMatching(options, builder);
    }

    private static CompletableFuture<Suggestions> suggestBlocksAndTags(SuggestionsBuilder builder) {
        List<String> options = new ArrayList<>(Registries.BLOCK.getIds().size());
        for (Identifier id : Registries.BLOCK.getIds()) {
            options.add(id.toString());
        }
        Registries.BLOCK.streamTags().forEach(tag -> {
            String tagId = tagId(tag);
            if (tagId != null) {
                options.add("#" + tagId);
            }
        });
        return CommandSource.suggestMatching(options, builder);
    }

    private static List<String> collectToolOptions() {
        List<String> options = new ArrayList<>(Registries.ITEM.getIds().size() + 1);
        for (Identifier id : Registries.ITEM.getIds()) {
            options.add(id.toString());
        }
        Registries.ITEM.streamTags().forEach(tag -> {
            String tagId = tagId(tag);
            if (tagId != null) {
                options.add("#" + tagId);
            }
        });
        options.add(ConfigService.HAND_KEY);
        return options;
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

    private static boolean ensurePerToolMode(ServerCommandSource source, Bootstrap bootstrap) {
        if (bootstrap.configService().general().blockListMode().perTool()) {
            return true;
        }
        source.sendError(Translations.translate("command.veinminer.blockpertool.not_enabled"));
        return false;
    }

    private static Text describePerToolList(GeneralConfig.BlockListMode mode) {
        String key = mode.blacklist()
                ? "command.veinminer.block_list_type.per_tool_blacklist"
                : "command.veinminer.block_list_type.per_tool_whitelist";
        return Translations.translate(key);
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

}
