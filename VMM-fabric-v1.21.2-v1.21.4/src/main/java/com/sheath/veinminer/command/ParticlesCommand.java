package com.sheath.veinminer.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.player.PlayerSettingsStore;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.sheath.veinminer.util.Translations;

import java.util.function.Consumer;

final class ParticlesCommand {

    private ParticlesCommand() {
    }

    static ArgumentBuilder<ServerCommandSource, ?> buildPlayer(Bootstrap bootstrap) {
        return CommandManager.literal("particles")
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
        return withPlayer(source, player -> {
            PlayerSettingsStore store = bootstrap.playerSettings();
            boolean newState = !store.isParticlesEnabled(player);
            store.setParticlesEnabled(player, newState);
            store.saveAsync();
            player.sendMessage(Translations.translate("command.veinminer.particles.toggle",
                    Translations.translate(newState ? "command.veinminer.enabled" : "command.veinminer.disabled")), true);
        });
    }

    private static int setColor(ServerCommandSource source,
                                Bootstrap bootstrap,
                                int red,
                                int green,
                                int blue) {
        return withPlayer(source, player -> {
            bootstrap.playerSettings().setParticleColor(player, red, green, blue);
            bootstrap.playerSettings().saveAsync();
            player.sendMessage(Translations.translate("command.veinminer.particles.color_set"), true);
        });
    }

    private static int setDuration(ServerCommandSource source,
                                   Bootstrap bootstrap,
                                   int ticks) {
        int maxTicks = PlayerSettingsStore.MAX_PARTICLE_DURATION_TICKS;
        if (ticks > maxTicks) {
            source.sendError(Translations.translate(
                    "command.veinminer.particles.duration_too_high",
                    ticks,
                    maxTicks,
                    maxTicks / 20));
            return 0;
        }

        return withPlayer(source, player -> {
            bootstrap.playerSettings().setParticleDurationTicks(player, ticks);
            bootstrap.playerSettings().saveAsync();
            player.sendMessage(Translations.translate("command.veinminer.particles.duration_set", ticks), true);
        });
    }

    private static int withPlayer(ServerCommandSource source, Consumer<ServerPlayerEntity> consumer) {
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (player == null) {
                source.sendError(Translations.translate("command.veinminer.player_only"));
                return 0;
            }
            consumer.accept(player);
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal(ex.getMessage() == null ? "Unknown error" : ex.getMessage()));
            return 0;
        }
    }

}
