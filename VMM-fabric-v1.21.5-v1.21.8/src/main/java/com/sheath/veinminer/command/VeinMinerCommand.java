package com.sheath.veinminer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sheath.veinminer.config.ConfigService.ConfigLoadException;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.player.PlayerSettingsStore.MessageType;
import com.sheath.veinminer.util.Log;
import com.sheath.veinminer.util.Translations;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class VeinMinerCommand {

    private static final String[] MAIN_HELP_TOPICS = {
            "toggle",
            "reload",
            "activation",
            "togglemessages",
            "particles"
    };

    private static final String[] ADMIN_HELP_TOPICS = {
            "blocks",
            "tools",
            "settings",
            "reload",
            "confirm",
            "cancel"
    };

    private static final String[] ADVANCED_HELP_TOPICS = {
            "blockpertool",
            "settings",
            "test",
            "confirm",
            "cancel"
    };

    private final Bootstrap bootstrap;

    public VeinMinerCommand(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("veinminer")
                .requires(source -> hasPermissionLevel(source, 0))
                .executes(ctx -> showMainHelp(ctx.getSource()))
                .then(buildHelpNode(MAIN_HELP_TOPICS, this::showMainHelp, this::showMainTopicHelp))
                .then(CommandManager.literal("toggle")
                        .executes(ctx -> withPlayer(ctx.getSource(), this::toggleVeinminer)))
                .then(CommandManager.literal("reload")
                        .requires(this::canAdmin)
                        .executes(ctx -> reload(ctx.getSource())))
                .then(buildToggleMessagesNode())
                .then(buildActivationNode())
                .then(ParticlesCommand.buildPlayer(bootstrap));

        dispatcher.register(root);
        dispatcher.register(buildAdminRoot());

        if (bootstrap.configService().general().advanced().enabled()) {
            dispatcher.register(buildAdvancedRoot());
        }
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildAdminRoot() {
        var root = CommandManager.literal("vmadmin")
                .requires(this::canAdmin)
                .executes(ctx -> showAdminHelp(ctx.getSource()))
                .then(buildHelpNode(ADMIN_HELP_TOPICS, this::showAdminHelp, this::showAdminTopicHelp));

        root.then(BlocksCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(ToolsCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(SettingsCommand.buildAdmin(bootstrap, this::canAdmin));
        root.then(CommandManager.literal("reload")
                .executes(ctx -> reload(ctx.getSource())));
        root.then(CommandManager.literal("confirm")
                .executes(ctx -> bootstrap.confirmations().confirm(ctx.getSource())));
        root.then(CommandManager.literal("cancel")
                .executes(ctx -> bootstrap.confirmations().cancel(ctx.getSource())));
        return root;
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildAdvancedRoot() {
        var root = CommandManager.literal("vmadvanced")
                .requires(this::canAdmin)
                .executes(ctx -> showAdvancedHelp(ctx.getSource()))
                .then(buildHelpNode(ADVANCED_HELP_TOPICS, this::showAdvancedHelp, this::showAdvancedTopicHelp));

        root.then(BlockPerToolCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(SettingsCommand.buildAdvanced(bootstrap, this::canAdmin));
        root.then(TestCommand.build(bootstrap, this::canAdmin));
        root.then(CommandManager.literal("confirm")
                .executes(ctx -> bootstrap.confirmations().confirm(ctx.getSource())));
        root.then(CommandManager.literal("cancel")
                .executes(ctx -> bootstrap.confirmations().cancel(ctx.getSource())));

        return root;
    }

    private ArgumentBuilder<ServerCommandSource, ?> buildHelpNode(String[] topics,
                                                                   Function<ServerCommandSource, Integer> rootHelp,
                                                                   BiFunction<ServerCommandSource, String, Integer> topicHelp) {
        return CommandManager.literal("help")
                .executes(ctx -> rootHelp.apply(ctx.getSource()))
                .then(CommandManager.argument("topic", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String topic : topics) {
                                builder.suggest(topic);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> topicHelp.apply(ctx.getSource(), StringArgumentType.getString(ctx, "topic"))));
    }

    private int showMainHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Translations.translate("command.veinminer.help"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showMainTopicHelp(ServerCommandSource source, String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "toggle" -> sendTopicHelp(source, "command.veinminer.help.toggle");
            case "reload" -> sendTopicHelp(source, "command.veinminer.help.reload");
            case "activation" -> sendTopicHelp(source, "command.veinminer.help.activation");
            case "togglemessages" -> sendTopicHelp(source, "command.veinminer.help.togglemessages");
            case "particles" -> sendTopicHelp(source, "command.veinminer.help.particles");
            default -> sendUnknownTopicHelp(source, "veinminer", topic);
        };
    }

    private int showAdminHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Translations.translate("command.veinminer.help.admin"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showAdminTopicHelp(ServerCommandSource source, String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "blocks" -> sendTopicHelp(source, "command.veinminer.help.blocks");
            case "tools" -> sendTopicHelp(source, "command.veinminer.help.tools");
            case "settings" -> sendTopicHelp(source, "command.veinminer.help.settings");
            case "reload" -> sendTopicHelp(source, "command.veinminer.help.reload");
            case "confirm" -> sendTopicHelp(source, "command.veinminer.help.confirm");
            case "cancel" -> sendTopicHelp(source, "command.veinminer.help.cancel");
            default -> sendUnknownTopicHelp(source, "vmadmin", topic);
        };
    }

    private int showAdvancedHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Translations.translate("command.veinminer.help.advanced"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showAdvancedTopicHelp(ServerCommandSource source, String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "blockpertool" -> sendTopicHelp(source, "command.veinminer.help.blockpertool");
            case "settings" -> sendTopicHelp(source, "command.veinminer.help.settings");
            case "test" -> sendTopicHelp(source, "command.veinminer.help.test");
            case "confirm" -> sendTopicHelp(source, "command.veinminer.help.confirm");
            case "cancel" -> sendTopicHelp(source, "command.veinminer.help.cancel");
            default -> sendUnknownTopicHelp(source, "vmadvanced", topic);
        };
    }

    private int sendTopicHelp(ServerCommandSource source, String translationKey) {
        source.sendFeedback(() -> Translations.translate(translationKey), false);
        return Command.SINGLE_SUCCESS;
    }

    private int sendUnknownTopicHelp(ServerCommandSource source, String rootLiteral, String topic) {
        source.sendError(Translations.translate("command.veinminer.help.unknown_topic", topic, rootLiteral));
        return 0;
    }

    private int reload(ServerCommandSource source) {
        try {
            bootstrap.configService().loadAll();
            bootstrap.controller().reloadFromConfig();
            source.sendFeedback(() -> Translations.translate("command.veinminer.reload"), true);
            return Command.SINGLE_SUCCESS;
        } catch (ConfigLoadException ex) {
            Log.error("Config reload failed", ex);
            String fileName = ex.path().getFileName().toString();
            int line = ex.line();
            Object lineArg = line >= 0 ? line : Text.literal("?");
            source.sendError(Translations.translate("command.veinminer.reload_failed", fileName, lineArg, ex.getMessage()));
            return 0;
        } catch (Exception ex) {
            Log.error("Config reload failed", ex);
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            source.sendError(Translations.translate("command.veinminer.reload_failed", message));
            return 0;
        }
    }

    private void toggleVeinminer(ServerPlayerEntity player) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean newState = !store.isVeinminerEnabled(player);
        store.setVeinminerEnabled(player, newState);
        store.saveAsync();
        player.sendMessage(Translations.translate("command.veinminer.toggle",
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
    }

    private ArgumentBuilder<ServerCommandSource, ?> buildToggleMessagesNode() {
        var root = CommandManager.literal("togglemessages");
        for (MessageType type : MessageType.values()) {
            root.then(CommandManager.literal(type.id())
                    .executes(ctx -> withPlayer(ctx.getSource(), player -> toggleMessage(player, type))));
        }
        return root;
    }

    private ArgumentBuilder<ServerCommandSource, ?> buildActivationNode() {
        return CommandManager.literal("activation")
                .then(CommandManager.literal("keybind")
                        .executes(ctx -> withPlayer(ctx.getSource(), this::toggleActivationInput)))
                .then(CommandManager.literal("mode")
                        .then(CommandManager.literal("hold")
                                .executes(ctx -> withPlayer(ctx.getSource(), player -> setActivationMode(player, false))))
                        .then(CommandManager.literal("toggle")
                                .executes(ctx -> withPlayer(ctx.getSource(), player -> setActivationMode(player, true)))));
    }

    private void toggleMessage(ServerPlayerEntity player, MessageType type) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean newState = !store.isMessageEnabled(player, type);
        store.setMessageEnabled(player, type, newState);
        store.saveAsync();
        player.sendMessage(Translations.translate("command.veinminer.togglemessage",
                Text.literal(capitalize(type.id())),
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
    }

    private int withPlayer(ServerCommandSource source, Consumer<ServerPlayerEntity> consumer) {
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (player == null) {
                source.sendError(Translations.translate("command.veinminer.player_only"));
                return 0;
            }
            consumer.accept(player);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            Log.error("Command execution failed", ex);
            source.sendError(Text.literal(ex.getMessage() == null ? "Unknown error" : ex.getMessage()));
            return 0;
        }
    }

    private void toggleActivationInput(ServerPlayerEntity player) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean useKeybind = store.useKeybind(player);
        boolean nextUseKeybind = !useKeybind;
        if (nextUseKeybind && !bootstrap.keyStates().hasClient(player)) {
            player.sendMessage(Translations.translate("command.veinminer.activation.keybind.client_required"), false);
            return;
        }
        store.setUseKeybind(player, nextUseKeybind);
        store.resetKeyToggleState(player);
        store.resetCrouchToggleState(player);
        store.saveAsync();
        player.sendMessage(Translations.translate(
                "command.veinminer.activation.input",
                Translations.translate(nextUseKeybind
                        ? "command.veinminer.activation.input.keybind"
                        : "command.veinminer.activation.input.shift")),
                true);
    }

    private void setActivationMode(ServerPlayerEntity player, boolean toggleMode) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        store.setKeyToggleMode(player, toggleMode);
        store.resetKeyToggleState(player);
        store.setCrouchToggleMode(player, toggleMode);
        store.resetCrouchToggleState(player);
        store.saveAsync();
        player.sendMessage(Translations.translate(
                "command.veinminer.activation.mode",
                Translations.translate(toggleMode
                        ? "command.veinminer.activation.mode.toggle"
                        : "command.veinminer.activation.mode.hold")),
                true);
    }

    private static String capitalize(String input) {
        if (input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    private boolean canAdmin(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)
                    || bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.SETTINGS_MANAGE);
        }
        return hasPermissionLevel(source, 2);
    }

    private static boolean hasPermissionLevel(ServerCommandSource source, int level) {
        if (level <= 0) {
            return true;
        }

        Boolean legacy = invokeLegacyPermissionCheck(source, level);
        if (legacy != null) {
            return legacy;
        }

        Object permissions = invokeNoArg(source, "getPermissions");
        Boolean predicateCheck = checkPermissionPredicate(permissions, level);
        return predicateCheck != null ? predicateCheck : false;
    }

    private static Boolean invokeLegacyPermissionCheck(Object target, int level) {
        try {
            Method method = target.getClass().getMethod("hasPermissionLevel", int.class);
            Object result = method.invoke(target, level);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Boolean checkPermissionPredicate(Object permissionsPredicate, int level) {
        if (permissionsPredicate == null) {
            return null;
        }

        Object permissionToken = resolvePermissionToken(level, permissionsPredicate.getClass().getClassLoader());
        if (permissionToken == null) {
            return null;
        }

        for (Method method : permissionsPredicate.getClass().getMethods()) {
            if (!method.getName().equals("hasPermission") || method.getParameterCount() != 1) {
                continue;
            }
            try {
                Object result = method.invoke(permissionsPredicate, permissionToken);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static Object resolvePermissionToken(int level, ClassLoader classLoader) {
        for (String className : new String[]{"net.minecraft.command.DefaultPermissions", "net.minecraft.command.LeveledPermissionPredicate"}) {
            try {
                Class<?> permissionClass = Class.forName(className, false, classLoader);
                for (String fieldName : permissionFieldCandidates(level)) {
                    try {
                        return permissionClass.getField(fieldName).get(null);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static String[] permissionFieldCandidates(int level) {
        return switch (level) {
            case 1 -> new String[]{"MODERATORS", "LEVEL_1"};
            case 2 -> new String[]{"GAMEMASTERS", "LEVEL_2", "MODERATORS"};
            case 3 -> new String[]{"ADMINS", "LEVEL_3", "GAMEMASTERS"};
            default -> new String[]{"OWNERS", "LEVEL_4", "ADMINS"};
        };
    }

    private static final class Command {
        static final int SINGLE_SUCCESS = 1;

        private Command() {
        }
    }
}
