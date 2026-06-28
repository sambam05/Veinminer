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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("veinminer")
                .requires(source -> bootstrap.permissionService().hasPermissionLevel(source, 0))
                .executes(ctx -> showMainHelp(ctx.getSource()))
                .then(buildHelpNode(MAIN_HELP_TOPICS, this::showMainHelp, this::showMainTopicHelp))
                .then(Commands.literal("toggle")
                        .executes(ctx -> withPlayer(ctx.getSource(), this::toggleVeinminer)))
                .then(Commands.literal("reload")
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

    private LiteralArgumentBuilder<CommandSourceStack> buildAdminRoot() {
        var root = Commands.literal("vmadmin")
                .requires(this::canAdmin)
                .executes(ctx -> showAdminHelp(ctx.getSource()))
                .then(buildHelpNode(ADMIN_HELP_TOPICS, this::showAdminHelp, this::showAdminTopicHelp));

        root.then(BlocksCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(ToolsCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(SettingsCommand.buildAdmin(bootstrap, this::canAdmin));
        root.then(Commands.literal("reload")
                .executes(ctx -> reload(ctx.getSource())));
        root.then(Commands.literal("confirm")
                .executes(ctx -> bootstrap.confirmations().confirm(ctx.getSource())));
        root.then(Commands.literal("cancel")
                .executes(ctx -> bootstrap.confirmations().cancel(ctx.getSource())));
        return root;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildAdvancedRoot() {
        var root = Commands.literal("vmadvanced")
                .requires(this::canAdmin)
                .executes(ctx -> showAdvancedHelp(ctx.getSource()))
                .then(buildHelpNode(ADVANCED_HELP_TOPICS, this::showAdvancedHelp, this::showAdvancedTopicHelp));

        root.then(BlockPerToolCommand.build(bootstrap, this::canAdmin, bootstrap.confirmations()));
        root.then(SettingsCommand.buildAdvanced(bootstrap, this::canAdmin));
        root.then(TestCommand.build(bootstrap, this::canAdmin));
        root.then(Commands.literal("confirm")
                .executes(ctx -> bootstrap.confirmations().confirm(ctx.getSource())));
        root.then(Commands.literal("cancel")
                .executes(ctx -> bootstrap.confirmations().cancel(ctx.getSource())));

        return root;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildHelpNode(String[] topics,
                                                                  Function<CommandSourceStack, Integer> rootHelp,
                                                                  BiFunction<CommandSourceStack, String, Integer> topicHelp) {
        return Commands.literal("help")
                .executes(ctx -> rootHelp.apply(ctx.getSource()))
                .then(Commands.argument("topic", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String topic : topics) {
                                builder.suggest(topic);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> topicHelp.apply(ctx.getSource(), StringArgumentType.getString(ctx, "topic"))));
    }

    private int showMainHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showMainTopicHelp(CommandSourceStack source, String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "toggle" -> sendTopicHelp(source, "command.veinminer.help.toggle");
            case "reload" -> sendTopicHelp(source, "command.veinminer.help.reload");
            case "activation" -> sendTopicHelp(source, "command.veinminer.help.activation");
            case "togglemessages" -> sendTopicHelp(source, "command.veinminer.help.togglemessages");
            case "particles" -> sendTopicHelp(source, "command.veinminer.help.particles");
            default -> sendUnknownTopicHelp(source, "veinminer", topic);
        };
    }

    private int showAdminHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.admin"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showAdminTopicHelp(CommandSourceStack source, String topic) {
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

    private int showAdvancedHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.advanced"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showAdvancedTopicHelp(CommandSourceStack source, String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "blockpertool" -> sendTopicHelp(source, "command.veinminer.help.blockpertool");
            case "settings" -> sendTopicHelp(source, "command.veinminer.help.settings");
            case "test" -> sendTopicHelp(source, "command.veinminer.help.test");
            case "confirm" -> sendTopicHelp(source, "command.veinminer.help.confirm");
            case "cancel" -> sendTopicHelp(source, "command.veinminer.help.cancel");
            default -> sendUnknownTopicHelp(source, "vmadvanced", topic);
        };
    }

    private int sendTopicHelp(CommandSourceStack source, String translationKey) {
        source.sendSuccess(() -> Translations.translate(translationKey), false);
        return Command.SINGLE_SUCCESS;
    }

    private int sendUnknownTopicHelp(CommandSourceStack source, String rootLiteral, String topic) {
        source.sendFailure(Translations.translate("command.veinminer.help.unknown_topic", topic, rootLiteral));
        return 0;
    }

    private int reload(CommandSourceStack source) {
        try {
            bootstrap.configService().loadAll();
            bootstrap.controller().reloadFromConfig();
            source.sendSuccess(() -> Translations.translate("command.veinminer.reload"), true);
            return Command.SINGLE_SUCCESS;
        } catch (ConfigLoadException ex) {
            Log.error("Config reload failed", ex);
            String fileName = ex.path().getFileName().toString();
            int line = ex.line();
            String lineArg = line >= 0 ? Integer.toString(line) : "?";
            source.sendFailure(Translations.translate("command.veinminer.reload_failed", fileName, lineArg, ex.getMessage()));
            return 0;
        } catch (Exception ex) {
            Log.error("Config reload failed", ex);
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            source.sendFailure(Translations.translate("command.veinminer.reload_failed", message));
            return 0;
        }
    }

    private void toggleVeinminer(ServerPlayer player) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean newState = !store.isVeinminerEnabled(player);
        store.setVeinminerEnabled(player, newState);
        store.saveAsync();
        player.displayClientMessage(Translations.translate("command.veinminer.toggle",
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildToggleMessagesNode() {
        var root = Commands.literal("togglemessages");
        for (MessageType type : MessageType.values()) {
            root.then(Commands.literal(type.id())
                    .executes(ctx -> withPlayer(ctx.getSource(), player -> toggleMessage(player, type))));
        }
        return root;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildActivationNode() {
        return Commands.literal("activation")
                .then(Commands.literal("keybind")
                        .executes(ctx -> withPlayer(ctx.getSource(), this::toggleActivationInput)))
                .then(Commands.literal("mode")
                        .then(Commands.literal("hold")
                                .executes(ctx -> withPlayer(ctx.getSource(), player -> setActivationMode(player, false))))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> withPlayer(ctx.getSource(), player -> setActivationMode(player, true)))));
    }

    private void toggleMessage(ServerPlayer player, MessageType type) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean newState = !store.isMessageEnabled(player, type);
        store.setMessageEnabled(player, type, newState);
        store.saveAsync();
        player.displayClientMessage(Translations.translate("command.veinminer.togglemessage",
                Component.literal(capitalize(type.id())),
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
    }

    private int withPlayer(CommandSourceStack source, Consumer<ServerPlayer> consumer) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Translations.translate("command.veinminer.player_only"));
                return 0;
            }
            consumer.accept(player);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            Log.error("Command execution failed", ex);
            source.sendFailure(Component.literal(ex.getMessage() == null ? "Unknown error" : ex.getMessage()));
            return 0;
        }
    }

    private void toggleActivationInput(ServerPlayer player) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean useKeybind = store.useKeybind(player);
        boolean nextUseKeybind = !useKeybind;
        if (nextUseKeybind && !bootstrap.keyStates().hasClient(player)) {
            player.displayClientMessage(Translations.translate("command.veinminer.activation.keybind.client_required"), false);
            return;
        }
        store.setUseKeybind(player, nextUseKeybind);
        store.resetKeyToggleState(player);
        store.resetCrouchToggleState(player);
        store.saveAsync();
        player.displayClientMessage(Translations.translate(
                "command.veinminer.activation.input",
                Translations.translate(nextUseKeybind
                        ? "command.veinminer.activation.input.keybind"
                        : "command.veinminer.activation.input.shift")),
                true);
    }

    private void setActivationMode(ServerPlayer player, boolean toggleMode) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        store.setKeyToggleMode(player, toggleMode);
        store.resetKeyToggleState(player);
        store.setCrouchToggleMode(player, toggleMode);
        store.resetCrouchToggleState(player);
        store.saveAsync();
        player.displayClientMessage(Translations.translate(
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

    private boolean canAdmin(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)
                    || bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.SETTINGS_MANAGE);
        }
        return bootstrap.permissionService().hasPermissionLevel(source, 2);
    }

    private static final class Command {
        static final int SINGLE_SUCCESS = 1;

        private Command() {
        }
    }
}
