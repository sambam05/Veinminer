package com.sheath.veinminer.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.testing.FeatureTestHarness;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.sheath.veinminer.util.Translations;

import java.util.function.Predicate;

public final class TestCommand {

    private TestCommand() {}

    public static ArgumentBuilder<CommandSourceStack, ?> build(Bootstrap bootstrap,
                                                               Predicate<CommandSourceStack> permissionCheck) {
        FeatureTestHarness harness = bootstrap.featureTestHarness();
        return Commands.literal("test")
                .requires(permissionCheck::test)
                .executes(ctx -> execute(harness, ctx.getSource()));
    }

    private static int execute(FeatureTestHarness harness, CommandSourceStack source) {
        if (!harness.start(source)) {
            source.sendFailure(Translations.translate("command.veinminer.test.busy"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
