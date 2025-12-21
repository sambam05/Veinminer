package com.sheath.veinminer.command;

import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SetupWelcomePrompt {

    private SetupWelcomePrompt() {
    }

    public static void maybeSend(Bootstrap bootstrap, ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (bootstrap.configService().general().setupWizardPromptComplete()) {
            return;
        }
        if (!bootstrap.permissionService().hasPermission(player, VeinMinerController.Permissions.RELOAD)) {
            return;
        }

        MutableText yes = button("Yes",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/veinminer setup welcome yes"),
                Text.literal("Start the setup wizard"))
                .formatted(Formatting.GREEN);
        MutableText no = button("No",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/veinminer setup welcome no"),
                Text.literal("Ignore for now"))
                .formatted(Formatting.RED);

        player.sendMessage(Text.literal("[Veinminer] ").formatted(Formatting.GOLD)
                .append(Text.literal("Run the setup wizard now? ").formatted(Formatting.YELLOW))
                .append(yes)
                .append(Text.literal(" "))
                .append(no), false);

        player.sendMessage(Text.literal("You can run it later with ").formatted(Formatting.GRAY)
                .append(suggestButton("/veinminer setup", "/veinminer setup"))
                .append(Text.literal(".").formatted(Formatting.GRAY)), false);
    }

    private static MutableText button(String label, ClickEvent click, Text hover) {
        return Text.literal("[" + label + "]")
                .styled(style -> style.withColor(Formatting.AQUA)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static MutableText suggestButton(String label, String suggestion) {
        return Text.literal(label)
                .styled(style -> style.withColor(Formatting.AQUA)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(suggestion))));
    }
}


