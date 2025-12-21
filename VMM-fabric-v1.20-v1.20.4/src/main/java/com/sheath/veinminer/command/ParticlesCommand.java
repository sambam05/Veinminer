package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.sheath.veinminer.util.Translations;

import java.util.function.Predicate;

final class ParticlesCommand {

    private ParticlesCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                         Predicate<ServerCommandSource> managePermission) {
        return CommandManager.literal("particles")
                .requires(managePermission::test)
                .then(CommandManager.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(CommandManager.literal("toggle").executes(ctx -> toggleParticles(ctx.getSource(), bootstrap)))
                .then(CommandManager.literal("setcolor")
                        .then(CommandManager.argument("red", IntegerArgumentType.integer(0, 255))
                                .then(CommandManager.argument("green", IntegerArgumentType.integer(0, 255))
                                        .then(CommandManager.argument("blue", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> setColor(
                                                        ctx.getSource(),
                                                        bootstrap,
                                                        IntegerArgumentType.getInteger(ctx, "red"),
                                                        IntegerArgumentType.getInteger(ctx, "green"),
                                                        IntegerArgumentType.getInteger(ctx, "blue")))))))
                .then(CommandManager.literal("setduration")
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> setDuration(
                                        ctx.getSource(),
                                        bootstrap,
                                        IntegerArgumentType.getInteger(ctx, "ticks")))));
    }

    private static int toggleParticles(ServerCommandSource source,
                                       Bootstrap bootstrap) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.PARTICLES_MANAGE,
                VeinMinerController.Permissions.PARTICLES_ENABLE,
                VeinMinerController.Permissions.PARTICLES_DISABLE)) {
            return 0;
        }
        var particles = bootstrap.configService().general().particles();
        boolean newState = !particles.enabled();
        particles.setEnabled(newState);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.particles.toggle",
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        return 1;
    }

    private static int setColor(ServerCommandSource source,
                                Bootstrap bootstrap,
                                int red,
                                int green,
                                int blue) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.PARTICLES_MANAGE,
                VeinMinerController.Permissions.PARTICLES_SETCOLOR)) {
            return 0;
        }
        var particles = bootstrap.configService().general().particles();
        particles.setRed(red);
        particles.setGreen(green);
        particles.setBlue(blue);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.particles.color_set"), true);
        return 1;
    }

    private static int setDuration(ServerCommandSource source,
                                   Bootstrap bootstrap,
                                   int ticks) {
        if (!ensurePermission(source, bootstrap,
                VeinMinerController.Permissions.PARTICLES_MANAGE,
                VeinMinerController.Permissions.PARTICLES_SETDURATION)) {
            return 0;
        }
        bootstrap.configService().general().particles().setDurationTicks(ticks);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendFeedback(() -> Translations.translate("command.veinminer.particles.duration_set", ticks), true);
        return 1;
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Translations.translate("command.veinminer.help.particles"), false);
        return 1;
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
}
