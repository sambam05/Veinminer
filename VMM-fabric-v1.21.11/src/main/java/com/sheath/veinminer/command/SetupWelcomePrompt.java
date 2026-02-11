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

        MutableText yes = button("Yes", "/veinminer setup welcome yes", Text.literal("Start the setup wizard"))
                .formatted(Formatting.GREEN);
        MutableText no = button("No", "/veinminer setup welcome no", Text.literal("Ignore for now"))
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

    private static MutableText button(String label, String command, Text hover) {
        return Text.literal("[" + label + "]")
                .styled(style -> {
                    var result = style.withColor(Formatting.AQUA);
                    ClickEvent click = createClickEvent("RUN_COMMAND", command, "runCommand");
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hoverEvent = createHoverEvent(hover);
                    if (hoverEvent != null) {
                        result = result.withHoverEvent(hoverEvent);
                    }
                    return result;
                });
    }

    private static MutableText suggestButton(String label, String suggestion) {
        return Text.literal(label)
                .styled(style -> {
                    var result = style.withColor(Formatting.AQUA).withUnderline(true);
                    ClickEvent click = createClickEvent("SUGGEST_COMMAND", suggestion, "suggestCommand");
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(Text.literal(suggestion));
                    if (hover != null) {
                        result = result.withHoverEvent(hover);
                    }
                    return result;
                });
    }

    @SuppressWarnings("unchecked")
    private static ClickEvent createClickEvent(String actionEnumName, String value, String factoryMethodName) {
        try {
            Class<?> actionClass = Class.forName("net.minecraft.text.ClickEvent$Action");
            if (actionClass.isEnum()) {
                Object action = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), actionEnumName);
                for (var ctor : ClickEvent.class.getConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 2 && params[0].isAssignableFrom(actionClass) && params[1] == String.class) {
                        return (ClickEvent) ctor.newInstance(action, value);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            return (ClickEvent) ClickEvent.class.getMethod(factoryMethodName, String.class).invoke(null, value);
        } catch (Throwable ignored) {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static HoverEvent createHoverEvent(Text text) {
        try {
            Class<?> actionClass = Class.forName("net.minecraft.text.HoverEvent$Action");
            if (actionClass.isEnum()) {
                Object action = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "SHOW_TEXT");
                for (var ctor : HoverEvent.class.getConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 2 && params[0].isAssignableFrom(actionClass) && params[1].isAssignableFrom(Text.class)) {
                        return (HoverEvent) ctor.newInstance(action, text);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            return (HoverEvent) HoverEvent.class.getMethod("showText", Text.class).invoke(null, text);
        } catch (Throwable ignored) {
        }

        return null;
    }
}
