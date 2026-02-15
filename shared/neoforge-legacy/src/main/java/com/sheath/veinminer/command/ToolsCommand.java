package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.state.ClearConfirmationManager;
import com.sheath.veinminer.util.Log;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class ToolsCommand {

    private ToolsCommand() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                        Predicate<CommandSourceStack> managePermission,
                                                        ClearConfirmationManager confirmations) {
        return Commands.literal("tools")
                .requires(managePermission::test)
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> list(ctx.getSource(), bootstrap))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), bootstrap)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestAllTools())
                                .executes(ctx -> add(ctx.getSource(), bootstrap,
                                        managePermission, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredTools(bootstrap))
                                .executes(ctx -> remove(ctx.getSource(), bootstrap,
                                        managePermission, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClear(ctx.getSource(), bootstrap, managePermission, confirmations)));
    }

    private static SuggestionProvider<CommandSourceStack> suggestAllTools() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(collectToolSuggestions(), builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestConfiguredTools(Bootstrap bootstrap) {
        return (ctx, builder) -> {
            List<String> options = new ArrayList<>(bootstrap.configService().snapshot().allowedTools().raw());
            if (options.isEmpty()) {
                return builder.buildFuture();
            }
            return SharedSuggestionProvider.suggest(options, builder);
        };
    }

    private static int requestClear(CommandSourceStack source,
                                    Bootstrap bootstrap,
                                    Predicate<CommandSourceStack> managePermission,
                                    ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, managePermission)) {
            return 0;
        }
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearAllowedTools();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendSuccess(() -> Translations.translate("command.veinminer.tools.cleared"), true);
            } else {
                confirmSource.sendFailure(Translations.translate("command.veinminer.tools.none"));
            }
        });
        return 1;
    }

    private static int list(CommandSourceStack source, Bootstrap bootstrap) {
        ConfigService.ConfigSnapshot snapshot = bootstrap.configService().snapshot();
        List<String> entries = List.copyOf(snapshot.allowedTools().raw());

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Translations.translate("command.veinminer.tools.none"), false);
            return 1;
        }

        source.sendSuccess(() -> Translations.translate("command.veinminer.tools.header"), false);
        entries.forEach(entry ->
                source.sendSuccess(() -> Translations.translate("command.veinminer.list_entry", entry), false));
        return 1;
    }

    private static List<String> collectToolSuggestions() {
        List<String> options = new ArrayList<>(BuiltInRegistries.ITEM.keySet().size() + 1);
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            options.add(id.toString());
        }
        addItemTags(options);
        options.add(ConfigService.HAND_KEY);
        return options;
    }

    private static void addItemTags(List<String> options) {
        try {
            Method method = BuiltInRegistries.ITEM.getClass().getMethod("getTagNames");
            Stream<TagKey<Item>> stream = (Stream<TagKey<Item>>) method.invoke(BuiltInRegistries.ITEM);
            stream.forEach(tag -> options.add("#" + tag.location()));
        } catch (Exception ignored) {
        }
    }

    private static int add(CommandSourceStack source,
                           Bootstrap bootstrap,
                           Predicate<CommandSourceStack> managePermission,
                           String rawEntry) {
        if (!ensurePermission(source, managePermission)) {
            return 0;
        }

        ConfigService.ChangeResult result = bootstrap.configService().addAllowedTool(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate("command.veinminer.tools.added", rawEntry), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendFailure(Translations.translate("command.veinminer.tools.exists", rawEntry));
                yield 0;
            }
            case INVALID -> {
                source.sendFailure(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when adding tool {}", result, rawEntry);
                source.sendFailure(Translations.translate("command.veinminer.tools.not_found", rawEntry));
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

        ConfigService.ChangeResult result = bootstrap.configService().removeAllowedTool(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendSuccess(() -> Translations.translate("command.veinminer.tools.removed", rawEntry), true);
                yield 1;
            }
            case NOT_FOUND -> {
                source.sendFailure(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            case INVALID -> {
                source.sendFailure(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when removing tool {}", result, rawEntry);
                source.sendFailure(Translations.translate("command.veinminer.tools.not_found", rawEntry));
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

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.tools"), false);
        return 1;
    }
}
