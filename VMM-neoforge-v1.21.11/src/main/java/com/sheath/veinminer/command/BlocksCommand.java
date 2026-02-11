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
import com.sheath.veinminer.util.Log;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class BlocksCommand {

    private BlocksCommand() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                        Predicate<CommandSourceStack> managePermission,
                                                        ClearConfirmationManager confirmations) {
        return Commands.literal("blocks")
                .requires(managePermission::test)
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> list(ctx.getSource(), bootstrap))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), bootstrap)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestRegistryBlocks())
                                .executes(ctx -> add(ctx.getSource(), bootstrap,
                                        managePermission, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredBlocks(bootstrap))
                                .executes(ctx -> remove(ctx.getSource(), bootstrap,
                                        managePermission, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClear(ctx.getSource(), bootstrap, managePermission, confirmations)));
    }

    private static SuggestionProvider<CommandSourceStack> suggestRegistryBlocks() {
        return (ctx, builder) -> suggestBlocksAndTags(builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestConfiguredBlocks(Bootstrap bootstrap) {
        return (ctx, builder) -> {
            List<String> suggestions = new ArrayList<>(bootstrap.configService().snapshot().allowedBlocks().raw());
            if (suggestions.isEmpty()) {
                return builder.buildFuture();
            }
            return SharedSuggestionProvider.suggest(suggestions, builder);
        };
    }

    private static int requestClear(CommandSourceStack source,
                                    Bootstrap bootstrap,
                                    Predicate<CommandSourceStack> managePermission,
                                    ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, managePermission)) {
            return 0;
        }
        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        Component descriptor = describeGlobalList(mode);
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearAllowedBlocks();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendSuccess(() -> Translations.translate(
                        "command.veinminer.blocks.cleared", descriptor), true);
            } else {
                confirmSource.sendFailure(Translations.translate("command.veinminer.blocks.none", descriptor));
            }
        });
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBlocksAndTags(SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>(BuiltInRegistries.BLOCK.keySet().size());
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            suggestions.add(id.toString());
        }
        addBlockTags(suggestions);
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static void addBlockTags(List<String> suggestions) {
        try {
            Method method = BuiltInRegistries.BLOCK.getClass().getMethod("getTagNames");
            Stream<TagKey<Block>> stream = (Stream<TagKey<Block>>) method.invoke(BuiltInRegistries.BLOCK);
            stream.forEach(tag -> suggestions.add("#" + tag.location()));
        } catch (Exception ignored) {
        }
    }

    private static int list(CommandSourceStack source, Bootstrap bootstrap) {
        ConfigService.ConfigSnapshot snapshot = bootstrap.configService().snapshot();
        List<String> entries = List.copyOf(snapshot.allowedBlocks().raw());
        GeneralConfig.BlockListMode mode = snapshot.general().blockListMode();
        Component descriptor = describeGlobalList(mode);

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.none", descriptor), false);
            if (mode.perTool()) {
                source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.per_tool_hint"), false);
            }
            return 1;
        }

        source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.header", descriptor), false);
        if (mode.perTool()) {
            source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.per_tool_hint"), false);
        }
        entries.forEach(entry ->
                source.sendSuccess(() -> Translations.translate("command.veinminer.list_entry", entry), false));
        return 1;
    }

    private static int add(CommandSourceStack source,
                           Bootstrap bootstrap,
                           Predicate<CommandSourceStack> managePermission,
                           String rawEntry) {
        if (!ensurePermission(source, managePermission)) {
            return 0;
        }

        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        ConfigService.ChangeResult result = bootstrap.configService().addAllowedBlock(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.added",
                        rawEntry,
                        describeGlobalList(mode)), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate("command.veinminer.blocks.exists",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            case INVALID -> {
                source.sendFailure(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when adding block {}", result, rawEntry);
                source.sendFailure(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
        };
    }

    private static int remove(CommandSourceStack source,
                              Bootstrap bootstrap,
                              Predicate<CommandSourceStack> managePermission,
                              String rawEntry) {
        if (!ensurePermission(source, managePermission)) {
            return 0;
        }

        GeneralConfig.BlockListMode mode = bootstrap.configService().general().blockListMode();
        ConfigService.ChangeResult result = bootstrap.configService().removeAllowedBlock(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate("command.veinminer.blocks.removed",
                        rawEntry,
                        describeGlobalList(mode)), true);
                yield 1;
            }
            case NOT_FOUND -> {
                source.sendFailure(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            case INVALID -> {
                source.sendFailure(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when removing block {}", result, rawEntry);
                source.sendFailure(Translations.translate("command.veinminer.blocks.not_found",
                        rawEntry,
                        describeGlobalList(mode)));
                yield 0;
            }
        };
    }

    private static boolean ensurePermission(CommandSourceStack source,
                                            Predicate<CommandSourceStack> managePermission) {
        if (managePermission.test(source)) {
            return true;
        }
        source.sendFailure(Translations.translate("message.veinminer.no_permission"));
        return false;
    }

    private static Component describeGlobalList(GeneralConfig.BlockListMode mode) {
        String key = mode.blacklist()
                ? "command.veinminer.block_list_type.global_blacklist"
                : "command.veinminer.block_list_type.global_whitelist";
        return Translations.translate(key);
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.blocks"), false);
        return 1;
    }
}

