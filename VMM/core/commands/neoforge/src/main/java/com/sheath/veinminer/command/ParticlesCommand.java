package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.player.PlayerSettingsStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.sheath.veinminer.util.Translations;

import java.util.function.Consumer;

final class ParticlesCommand {

    private ParticlesCommand() {}

    static ArgumentBuilder<CommandSourceStack, ?> buildPlayer(Bootstrap bootstrap) {
        return Commands.literal("particles")
                .then(Commands.literal("toggle").executes(ctx -> toggleParticles(ctx.getSource(), bootstrap)))
                .then(Commands.literal("setcolor")
                        .then(Commands.argument("red", IntegerArgumentType.integer(0, 255))
                                .then(Commands.argument("green", IntegerArgumentType.integer(0, 255))
                                        .then(Commands.argument("blue", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> setColor(
                                                        ctx.getSource(),
                                                        bootstrap,
                                                        IntegerArgumentType.getInteger(ctx, "red"),
                                                        IntegerArgumentType.getInteger(ctx, "green"),
                                                        IntegerArgumentType.getInteger(ctx, "blue")))))))
                .then(Commands.literal("setduration")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> setDuration(
                                        ctx.getSource(),
                                        bootstrap,
                                        IntegerArgumentType.getInteger(ctx, "ticks")))));
    }

    private static int toggleParticles(CommandSourceStack source,
                                       Bootstrap bootstrap) {
        return withPlayer(source, player -> {
            PlayerSettingsStore store = bootstrap.playerSettings();
            boolean newState = !store.isParticlesEnabled(player);
            store.setParticlesEnabled(player, newState);
            store.saveAsync();
            player.displayClientMessage(Translations.translate("command.veinminer.particles.toggle",
                    Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        });
    }

    private static int setColor(CommandSourceStack source,
                                Bootstrap bootstrap,
                                int red,
                                int green,
                                int blue) {
        return withPlayer(source, player -> {
            bootstrap.playerSettings().setParticleColor(player, red, green, blue);
            bootstrap.playerSettings().saveAsync();
            player.displayClientMessage(Translations.translate("command.veinminer.particles.color_set"), true);
        });
    }

    private static int setDuration(CommandSourceStack source,
                                   Bootstrap bootstrap,
                                   int ticks) {
        int maxTicks = PlayerSettingsStore.MAX_PARTICLE_DURATION_TICKS;
        if (ticks > maxTicks) {
            source.sendFailure(Translations.translate(
                    "command.veinminer.particles.duration_too_high",
                    ticks,
                    maxTicks,
                    maxTicks / 20));
            return 0;
        }

        return withPlayer(source, player -> {
            bootstrap.playerSettings().setParticleDurationTicks(player, ticks);
            bootstrap.playerSettings().saveAsync();
            player.displayClientMessage(Translations.translate("command.veinminer.particles.duration_set", ticks), true);
        });
    }

    private static int withPlayer(CommandSourceStack source, Consumer<ServerPlayer> consumer) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Translations.translate("command.veinminer.player_only"));
                return 0;
            }
            consumer.accept(player);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(ex.getMessage() == null ? "Unknown error" : ex.getMessage()));
            return 0;
        }
    }

}
