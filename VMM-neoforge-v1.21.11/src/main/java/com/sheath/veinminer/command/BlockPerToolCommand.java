package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.config.GeneralConfig;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.state.ClearConfirmationManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class BlockPerToolCommand {

    private BlockPerToolCommand() {}

    static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                        Predicate<CommandSourceStack> managePermission,
                                                        ClearConfirmationManager confirmations) {
        return Commands.literal("blockpertool")
                .requires(managePermission::test)
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(buildBlocksNode(bootstrap, managePermission, confirmations))
                .then(buildToolNode(bootstrap, managePermission, confirmations));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildBlocksNode(Bootstrap bootstrap,
                                                                          Predicate<CommandSourceStack> managePermission,
                                                                          ClearConfirmationManager confirmations) {
        return Commands.literal("blocks")
                .then(Commands.argument("tool", StringArgumentType.string())
                        .suggests(suggestConfiguredTools(bootstrap))
                        .then(Commands.literal("add")
                                .requires(managePermission::test)
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .suggests(suggestAllBlocksOrTags())
                                        .executes(ctx -> addBlock(
                                                ctx.getSource(),
                                                bootstrap,
                                                managePermission,
                                                StringArgumentType.getString(ctx, "tool"),
                                                StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("remove")
                                .requires(managePermission::test)
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .suggests(suggestBlocksForTool(bootstrap))
                                        .executes(ctx -> removeBlock(
                                                ctx.getSource(),
                                                bootstrap,
                                                managePermission,
                                                StringArgumentType.getString(ctx, "tool"),
                                                StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("list")
                                        .executes(ctx -> listBlocks(
                                                ctx.getSource(),
                                                bootstrap,
                                                StringArgumentType.getString(ctx, "tool"))))
                        .then(Commands.literal("clear")
                                .requires(managePermission::test)
                                .executes(ctx -> requestClearBlocks(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        confirmations,
                                        StringArgumentType.getString(ctx, "tool")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildToolNode(Bootstrap bootstrap,
                                                                        Predicate<CommandSourceStack> managePermission,
                                                                        ClearConfirmationManager confirmations) {
        return Commands.literal("tool")
                .then(Commands.literal("add")
                        .requires(managePermission::test)
                        .then(Commands.argument("tool", StringArgumentType.greedyString())
                                .suggests(suggestAllTools())
                                .executes(ctx -> addTool(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        StringArgumentType.getString(ctx, "tool")))))
                .then(Commands.literal("remove")
                        .requires(managePermission::test)
                        .then(Commands.argument("tool", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredTools(bootstrap))
                                .executes(ctx -> removeTool(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        StringArgumentType.getString(ctx, "tool")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listTools(ctx.getSource(), bootstrap)))
                .then(Commands.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClearAllTools(ctx.getSource(), bootstrap, managePermission, confirmations)));
    }

    private static int addBlock(CommandSourceStack source,
                                Bootstrap bootstrap,
                                Predicate<CommandSourceStack> managePermission,
                                String tool,
                                String block) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component listDescriptor = describePerToolList(mode);

        ConfigService.ChangeResult result = bootstrap.configService().addBlocksPerToolBlock(tool, block);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.block_added", block, listDescriptor, tool), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.block_exists", block, listDescriptor, tool));
                yield 0;
            }
            case INVALID, NOT_FOUND -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
            default -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
        };
    }

    private static int removeBlock(CommandSourceStack source,
                                   Bootstrap bootstrap,
                                   Predicate<CommandSourceStack> managePermission,
                                   String tool,
                                   String block) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component listDescriptor = describePerToolList(mode);

        ConfigService.ChangeResult result = bootstrap.configService().removeBlocksPerToolBlock(tool, block);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.block_removed", block, listDescriptor, tool), true);
                yield 1;
            }
            case NOT_FOUND, INVALID, ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.block_not_found", block, listDescriptor, tool));
                yield 0;
            }
        };
    }

    private static int listBlocks(CommandSourceStack source,
                                  Bootstrap bootstrap,
                                  String tool) {
        if (!ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component listDescriptor = describePerToolList(mode);
        NavigableSet<String> blocks = bootstrap.configService().getBlocksForTool(tool);
        if (blocks == null || blocks.isEmpty()) {
            source.sendSuccess(() -> Translations.translate(
                    "command.veinminer.blockpertool.no_blocks", listDescriptor, tool), false);
        } else {
            source.sendSuccess(() -> Translations.translate(
                    "command.veinminer.blockpertool.blocks_for_tool", listDescriptor, tool), false);
            blocks.forEach(block ->
                    source.sendSuccess(() -> Translations.translate("command.veinminer.list_entry", block), false));
        }
        return 1;
    }

    private static int requestClearBlocks(CommandSourceStack source,
                                          Bootstrap bootstrap,
                                          Predicate<CommandSourceStack> managePermission,
                                          ClearConfirmationManager confirmations,
                                          String tool) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        NavigableSet<String> blocks = bootstrap.configService().getBlocksForTool(tool);
        if (blocks == null) {
            source.sendFailure(Translations.translate(
                    "command.veinminer.blockpertool.tool_not_found", tool));
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component descriptor = describePerToolList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearBlocksForTool(tool);
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.blocks_cleared", descriptor, tool), true);
            } else {
                confirmSource.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.no_blocks", descriptor, tool));
            }
        });
        return 1;
    }

    private static int requestClearAllTools(CommandSourceStack source,
                                            Bootstrap bootstrap,
                                            Predicate<CommandSourceStack> managePermission,
                                            ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component descriptor = describePerToolList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearBlocksPerTool();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.tools_cleared", descriptor), true);
            } else {
                confirmSource.sendFailure(Translations.translate("command.veinminer.blockpertool.no_tools"));
            }
        });
        return 1;
    }

    private static int addTool(CommandSourceStack source,
                               Bootstrap bootstrap,
                               Predicate<CommandSourceStack> managePermission,
                               String tool) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        ConfigService.ChangeResult result = bootstrap.configService().addBlocksPerToolTool(tool);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.tool_added", tool), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.tool_exists", tool));
                yield 0;
            }
            case INVALID, NOT_FOUND -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.tool_not_found", tool));
                yield 0;
            }
        };
    }

    private static int removeTool(CommandSourceStack source,
                                  Bootstrap bootstrap,
                                  Predicate<CommandSourceStack> managePermission,
                                  String tool) {
        if (!ensurePermission(source, managePermission) || !ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        ConfigService.ChangeResult result = bootstrap.configService().removeBlocksPerToolTool(tool);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blockpertool.tool_removed", tool), true);
                yield 1;
            }
            case NOT_FOUND, INVALID, ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate(
                        "command.veinminer.blockpertool.tool_not_found", tool));
                yield 0;
            }
        };
    }

    private static int listTools(CommandSourceStack source, Bootstrap bootstrap) {
        if (!ensurePerToolMode(source, bootstrap)) {
            return 0;
        }
        NavigableSet<String> tools = bootstrap.configService().getAllToolKeys();
        if (tools == null || tools.isEmpty()) {
            source.sendSuccess(() -> Translations.translate("command.veinminer.blockpertool.no_tools"), false);
        } else {
            source.sendSuccess(() -> Translations.translate("command.veinminer.blockpertool.tools_header"), false);
            tools.forEach(tool ->
                    source.sendSuccess(() -> Translations.translate("command.veinminer.list_entry", tool), false));
        }
        return 1;
    }

    private static SuggestionProvider<CommandSourceStack> suggestConfiguredTools(Bootstrap bootstrap) {
        return (ctx, builder) -> suggestValues(bootstrap.configService().getAllToolKeys(), builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestAllTools() {
        return (ctx, builder) -> {
            List<String> quoted = new ArrayList<>();
            for (String option : collectToolOptions()) {
                quoted.add("\"" + option + "\""); // suggest only quoted tools so ids with ':'/'#' are one arg
            }
            return SharedSuggestionProvider.suggest(quoted, builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestAllBlocksOrTags() {
        return (ctx, builder) -> suggestBlocksAndTags(builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestBlocksForTool(Bootstrap bootstrap) {
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
            return SharedSuggestionProvider.suggest(entries, builder);
        };
    }

    private static CompletableFuture<Suggestions> suggestValues(Iterable<String> values, SuggestionsBuilder builder) {
        if (values == null) {
            return builder.buildFuture();
        }
        List<String> options = new ArrayList<>();
        for (String value : values) {
            options.add("\"" + value + "\""); // only quoted form for tool ids
        }
        if (options.isEmpty()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static CompletableFuture<Suggestions> suggestBlocksAndTags(SuggestionsBuilder builder) {
        List<String> options = new ArrayList<>(BuiltInRegistries.BLOCK.keySet().size());
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            options.add(id.toString());
        }
        addBlockTags(options);
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static List<String> collectToolOptions() {
        List<String> options = new ArrayList<>(BuiltInRegistries.ITEM.keySet().size() + 1);
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            options.add(id.toString());
        }
        addItemTags(options);
        options.add(ConfigService.HAND_KEY);
        return options;
    }

    private static void addBlockTags(List<String> options) {
        try {
            Method method = BuiltInRegistries.BLOCK.getClass().getMethod("getTagNames");
            Stream<TagKey<Block>> stream = (Stream<TagKey<Block>>) method.invoke(BuiltInRegistries.BLOCK);
            stream.forEach(tag -> options.add("#" + tag.location()));
        } catch (Exception ignored) {
        }
    }

    private static void addItemTags(List<String> options) {
        try {
            Method method = BuiltInRegistries.ITEM.getClass().getMethod("getTagNames");
            Stream<TagKey<Item>> stream = (Stream<TagKey<Item>>) method.invoke(BuiltInRegistries.ITEM);
            stream.forEach(tag -> options.add("#" + tag.location()));
        } catch (Exception ignored) {
        }
    }

    private static boolean ensurePerToolMode(CommandSourceStack source, Bootstrap bootstrap) {
        if (bootstrap.configService().general().blockListMode().perTool()) {
            return true;
        }
        source.sendFailure(Translations.translate("command.veinminer.blockpertool.not_enabled"));
        return false;
    }

    private static Component describePerToolList(GeneralConfig.BlockListMode mode) {
        String key = mode.blacklist()
                ? "command.veinminer.block_list_type.per_tool_blacklist"
                : "command.veinminer.block_list_type.per_tool_whitelist";
        return Translations.translate(key);
    }

    private static boolean ensurePermission(CommandSourceStack source,
                                            Predicate<CommandSourceStack> managePermission) {
        if (managePermission.test(source)) {
            return true;
        }
        source.sendFailure(Translations.translate("message.veinminer.no_permission"));
        return false;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.blockpertool"), false);
        return 1;
    }
}

