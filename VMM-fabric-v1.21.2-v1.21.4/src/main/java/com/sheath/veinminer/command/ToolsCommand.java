package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.state.ClearConfirmationManager;
import com.sheath.veinminer.util.Log;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class ToolsCommand {

    private ToolsCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                         Predicate<ServerCommandSource> managePermission,
                                                         ClearConfirmationManager confirmations) {
        return CommandManager.literal("tools")
                .requires(managePermission::test)
                .then(CommandManager.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> list(ctx.getSource(), bootstrap))
                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestAllTools())
                                .executes(ctx -> add(ctx.getSource(), bootstrap,
                                        StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestConfiguredTools(bootstrap))
                                .executes(ctx -> remove(ctx.getSource(), bootstrap,
                                        StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("clear")
                        .requires(managePermission::test)
                        .executes(ctx -> requestClear(ctx.getSource(), bootstrap, confirmations)));
    }

    private static int list(ServerCommandSource source, Bootstrap bootstrap) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.TOOLS_LIST,
                VeinMinerController.Permissions.TOOLS_MANAGE)) {
            return 0;
        }
        ConfigService.ConfigSnapshot snapshot = bootstrap.configService().snapshot();
        List<String> entries = List.copyOf(snapshot.allowedTools().raw());

        if (entries.isEmpty()) {
            source.sendFeedback(() -> Translations.translate("command.veinminer.tools.none"), false);
            return 1;
        }

        source.sendFeedback(() -> Translations.translate("command.veinminer.tools.header"), false);
        entries.forEach(entry ->
                source.sendFeedback(() -> Translations.translate("command.veinminer.list_entry", entry), false));
        return 1;
    }

    private static int add(ServerCommandSource source,
                           Bootstrap bootstrap,
                           String rawEntry) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.TOOLS_ADD,
                VeinMinerController.Permissions.TOOLS_MANAGE)) {
            return 0;
        }

        ConfigService.ChangeResult result = bootstrap.configService().addAllowedTool(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate("command.veinminer.tools.added", rawEntry), true);
                yield 1;
            }
            case ALREADY_PRESENT -> {
                source.sendError(Translations.translate("command.veinminer.tools.exists", rawEntry));
                yield 0;
            }
            case INVALID -> {
                source.sendError(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when adding tool {}", result, rawEntry);
                source.sendError(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
        };
    }

    private static int requestClear(ServerCommandSource source,
                                    Bootstrap bootstrap,
                                    ClearConfirmationManager confirmations) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.TOOLS_MANAGE)) {
            return 0;
        }
        confirmations.request(source, confirmSource -> {
            ConfigService.ChangeResult result = bootstrap.configService().clearAllowedTools();
            if (result == ConfigService.ChangeResult.SUCCESS) {
                bootstrap.controller().reloadFromConfig();
                confirmSource.sendFeedback(() -> Translations.translate("command.veinminer.tools.cleared"), true);
            } else {
                confirmSource.sendError(Translations.translate("command.veinminer.tools.none"));
            }
        });
        return 1;
    }

    private static int remove(ServerCommandSource source,
                              Bootstrap bootstrap,
                              String rawEntry) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.TOOLS_REMOVE,
                VeinMinerController.Permissions.TOOLS_MANAGE)) {
            return 0;
        }

        ConfigService.ChangeResult result = bootstrap.configService().removeAllowedTool(rawEntry);
        return switch (result) {
            case SUCCESS -> {
                bootstrap.controller().reloadFromConfig();
                source.sendFeedback(() -> Translations.translate("command.veinminer.tools.removed", rawEntry), true);
                yield 1;
            }
            case NOT_FOUND -> {
                source.sendError(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            case INVALID -> {
                source.sendError(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
            default -> {
                Log.warn("Unexpected change result {} when removing tool {}", result, rawEntry);
                source.sendError(Translations.translate("command.veinminer.tools.not_found", rawEntry));
                yield 0;
            }
        };
    }

    private static SuggestionProvider<ServerCommandSource> suggestAllTools() {
        return (ctx, builder) -> CommandSource.suggestMatching(collectToolSuggestions(), builder);
    }

    private static SuggestionProvider<ServerCommandSource> suggestConfiguredTools(Bootstrap bootstrap) {
        return (ctx, builder) -> {
            List<String> options = new ArrayList<>(bootstrap.configService().snapshot().allowedTools().raw());
            if (options.isEmpty()) {
                return builder.buildFuture();
            }
            return CommandSource.suggestMatching(options, builder);
        };
    }

    private static List<String> collectToolSuggestions() {
        List<String> options = new ArrayList<>(Registries.ITEM.getIds().size() + 1);
        Registries.ITEM.getIds().forEach(id -> options.add(id.toString()));
        Registries.ITEM.streamTags().forEach(tag -> {
            String tagId = tagId(tag);
            if (tagId != null) {
                options.add("#" + tagId);
            }
        });
        options.add(ConfigService.HAND_KEY);
        return options;
    }

    private static boolean ensurePermission(ServerCommandSource source,
                                            Bootstrap bootstrap,
                                            String... nodes) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return source.hasPermissionLevel(2);
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
        if (source.hasPermissionLevel(2)) {
            return true;
        }
        source.sendError(Translations.translate("message.veinminer.no_permission"));
        return false;
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
        source.sendFeedback(() -> Translations.translate("command.veinminer.help.tools"), false);
        return 1;
    }
}
