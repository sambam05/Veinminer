package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.sheath.veinminer.util.Translations;

import java.util.function.Predicate;

final class ParticlesCommand {

    private ParticlesCommand() {}

    static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                        Predicate<CommandSourceStack> managePermission) {
        return Commands.literal("particles")
                .requires(managePermission::test)
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(Commands.literal("toggle").executes(ctx -> toggleParticles(ctx.getSource(), bootstrap, managePermission)))
                .then(Commands.literal("setcolor")
                        .then(Commands.argument("red", IntegerArgumentType.integer(0, 255))
                                .then(Commands.argument("green", IntegerArgumentType.integer(0, 255))
                                        .then(Commands.argument("blue", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> setColor(
                                                        ctx.getSource(),
                                                        bootstrap,
                                                        managePermission,
                                                        IntegerArgumentType.getInteger(ctx, "red"),
                                                        IntegerArgumentType.getInteger(ctx, "green"),
                                                        IntegerArgumentType.getInteger(ctx, "blue")))))))
                .then(Commands.literal("setduration")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> setDuration(
                                        ctx.getSource(),
                                        bootstrap,
                                        managePermission,
                                        IntegerArgumentType.getInteger(ctx, "ticks")))));
    }

    private static int toggleParticles(CommandSourceStack source,
                                       Bootstrap bootstrap,
                                       Predicate<CommandSourceStack> managePermission) {
        if (!managePermission.test(source)) {
            source.sendFailure(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        var particles = bootstrap.configService().general().particles();
        boolean newState = !particles.enabled();
        particles.setEnabled(newState);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendSuccess(() -> Translations.translate("command.veinminer.particles.toggle",
                Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        return 1;
    }

    private static int setColor(CommandSourceStack source,
                                Bootstrap bootstrap,
                                Predicate<CommandSourceStack> managePermission,
                                int red,
                                int green,
                                int blue) {
        if (!managePermission.test(source)) {
            source.sendFailure(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        var particles = bootstrap.configService().general().particles();
        particles.setRed(red);
        particles.setGreen(green);
        particles.setBlue(blue);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendSuccess(() -> Translations.translate("command.veinminer.particles.color_set"), true);
        return 1;
    }

    private static int setDuration(CommandSourceStack source,
                                   Bootstrap bootstrap,
                                   Predicate<CommandSourceStack> managePermission,
                                   int ticks) {
        if (!managePermission.test(source)) {
            source.sendFailure(Translations.translate("message.veinminer.no_permission"));
            return 0;
        }
        bootstrap.configService().general().particles().setDurationTicks(ticks);
        bootstrap.configService().general().save();
        bootstrap.configService().rebuildSnapshot();
        bootstrap.controller().reloadFromConfig();
        source.sendSuccess(() -> Translations.translate("command.veinminer.particles.duration_set", ticks), true);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.translate("command.veinminer.help.particles"), false);
        return 1;
    }
}
