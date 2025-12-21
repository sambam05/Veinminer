package com.sheath.veinminer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.config.ConfigService.ConfigLoadException;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.player.PlayerSettingsStore.MessageType;
import com.sheath.veinminer.util.Log;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.sheath.veinminer.util.Translations;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class VeinMinerCommand {

    private static final Map<String, String> HELP_TOPICS = createHelpTopics();

    private final Bootstrap bootstrap;

    public VeinMinerCommand(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("veinminer")
                .requires(source -> source.hasPermission(0))
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource()))
                        .then(Commands.argument("topic", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    HELP_TOPICS.keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> showHelp(ctx.getSource(), StringArgumentType.getString(ctx, "topic")))))
                .then(Commands.literal("reload")
                        .requires(this::canReload)
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("toggle").executes(ctx -> withPlayer(ctx.getSource(), this::toggleVeinminer)))
                .then(Commands.literal("toggleparticles").executes(ctx -> withPlayer(ctx.getSource(), this::toggleParticles)))
                .then(buildToggleMessagesNode())
                .then(Commands.literal("confirm")
                        .requires(this::canReload)
                        .executes(ctx -> bootstrap.confirmations().confirm(ctx.getSource())))
                .then(Commands.literal("cancel")
                        .requires(this::canReload)
                        .executes(ctx -> bootstrap.confirmations().cancel(ctx.getSource())));

        root.then(BlocksCommand.build(bootstrap, this::canReload, bootstrap.confirmations()));
        root.then(ToolsCommand.build(bootstrap, this::canReload, bootstrap.confirmations()));
        root.then(BlockPerToolCommand.build(bootstrap, this::canReload, bootstrap.confirmations()));
        root.then(ParticlesCommand.build(bootstrap, this::canReload));
        root.then(SettingsCommand.build(bootstrap, this::canReload));
        root.then(SetupCommand.build(bootstrap, this::canReload));
        root.then(buildActivationNode());
        root.then(TestCommand.build(bootstrap, this::canReload));

        dispatcher.register(root);
    }

    private int showHelp(CommandSourceStack source) {
        return showHelp(source, "overview");
    }

    private int showHelp(CommandSourceStack source, String topic) {
        String normalized = topic == null ? "overview" : topic.toLowerCase(Locale.ROOT);
        String key = HELP_TOPICS.getOrDefault(normalized, HELP_TOPICS.get("overview"));
        source.sendSuccess(() -> Translations.translate(key), false);
        return Command.SINGLE_SUCCESS;
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

    private void toggleParticles(ServerPlayer player) {
        PlayerSettingsStore store = bootstrap.playerSettings();
        boolean newState = !store.isParticlesEnabled(player);
        store.setParticlesEnabled(player, newState);
        store.saveAsync();
        player.displayClientMessage(Translations.translate("command.veinminer.particles_player_toggle",
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildToggleMessagesNode() {
        var root = Commands.literal("togglemessages")
                .executes(ctx -> showToggleMessagesHelp(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> showToggleMessagesHelp(ctx.getSource())));
        for (MessageType type : MessageType.values()) {
            root.then(Commands.literal(type.id())
                    .executes(ctx -> withPlayer(ctx.getSource(), player -> toggleMessage(player, type))));
        }
        return root;
    }

        private ArgumentBuilder<CommandSourceStack, ?> buildActivationNode() {
        return Commands.literal("activation")
                .executes(ctx -> showActivationHelp(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> showActivationHelp(ctx.getSource())))
                .then(Commands.literal("keybind")
                        .executes(ctx -> withClientModPlayer(ctx.getSource(), player -> {
                            PlayerSettingsStore store = bootstrap.playerSettings();
                            boolean newState = !store.useKeybind(player);
                            store.setUseKeybind(player, newState);
                            store.resetKeyToggleState(player);
                            store.saveAsync();
                            player.displayClientMessage(Translations.translate(
                                    "command.veinminer.activation.keybind",
                                    Translations.translate(newState
                                            ? "command.veinminer.enabled"
                                            : "command.veinminer.disabled")), true);
                        })))
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

    private int withClientModPlayer(CommandSourceStack source, Consumer<ServerPlayer> consumer) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Translations.translate("command.veinminer.player_only"));
                return 0;
            }
            if (!bootstrap.keyStates().hasClient(player)) {
                player.displayClientMessage(Translations.translate("command.veinminer.activation.keybind.client_required"), false);
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

    private boolean hasClientMod(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        return bootstrap.keyStates().hasClient(player);
    }

    private static String capitalize(String input) {
        if (input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    private int showToggleMessagesHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.togglemessages"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showActivationHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.activation"), false);
        return Command.SINGLE_SUCCESS;
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

    private static Map<String, String> createHelpTopics() {
        return Map.ofEntries(
                Map.entry("overview", "command.veinminer.help"),
                Map.entry("blocks", "command.veinminer.help.blocks"),
                Map.entry("blockpertool", "command.veinminer.help.blockpertool"),
                Map.entry("tools", "command.veinminer.help.tools"),
                Map.entry("settings", "command.veinminer.help.settings"),
                Map.entry("blocklistmode", "command.veinminer.help.settings"),
                Map.entry("activation", "command.veinminer.help.activation"),
                Map.entry("togglemessages", "command.veinminer.help.togglemessages"),
                Map.entry("particles", "command.veinminer.help.particles"),
                Map.entry("setup", "command.veinminer.help.setup"),
                Map.entry("reload", "command.veinminer.help.reload"),
                Map.entry("toggle", "command.veinminer.help.toggle")
        );
    }

    private boolean canReload(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)
                    || bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.SETTINGS_MANAGE);
        }
        return source.hasPermission(2);
    }

    private static final class Command {
        static final int SINGLE_SUCCESS = 1;

        private Command() {}
    }
}



