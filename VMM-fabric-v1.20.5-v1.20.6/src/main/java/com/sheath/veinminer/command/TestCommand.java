package com.sheath.veinminer.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.testing.FeatureTestHarness;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import com.sheath.veinminer.util.Translations;

import java.util.function.Predicate;

public final class TestCommand {

    private TestCommand() {
    }

    public static ArgumentBuilder<ServerCommandSource, ?> build(Bootstrap bootstrap,
                                                               Predicate<ServerCommandSource> permissionCheck) {
        FeatureTestHarness harness = bootstrap.featureTestHarness();
        return CommandManager.literal("test")
                .requires(permissionCheck::test)
                .executes(ctx -> execute(harness, ctx.getSource()));
    }

    private static int execute(FeatureTestHarness harness, ServerCommandSource source) {
        if (!harness.start(source)) {
            source.sendError(Translations.translate("command.veinminer.test.busy"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
