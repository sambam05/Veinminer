package com.sheath.veinminer.command;

import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class SetupWelcomePrompt {

    private SetupWelcomePrompt() {
    }

    public static void maybeSend(Bootstrap bootstrap, ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (bootstrap.configService().general().setupWizardPromptComplete()) {
            return;
        }
        if (!bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)) {
            return;
        }

        MutableComponent yes = button("Yes",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/veinminer setup welcome yes"),
                Component.literal("Start the setup wizard"))
                .withStyle(ChatFormatting.GREEN);
        MutableComponent no = button("No",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/veinminer setup welcome no"),
                Component.literal("Ignore for now"))
                .withStyle(ChatFormatting.RED);

        player.displayClientMessage(Component.literal("[Veinminer] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Run the setup wizard now? ").withStyle(ChatFormatting.YELLOW))
                .append(yes)
                .append(Component.literal(" "))
                .append(no), false);

        player.displayClientMessage(Component.literal("You can run it later with ").withStyle(ChatFormatting.GRAY)
                .append(suggestButton("/veinminer setup", "/veinminer setup"))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)), false);
    }

    private static MutableComponent button(String label, ClickEvent click, Component hover) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static MutableComponent suggestButton(String label, String suggestion) {
        return Component.literal(label)
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(suggestion))));
    }
}

