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

        MutableComponent yes = button("Yes", "/veinminer setup welcome yes", Component.literal("Start the setup wizard"))
                .withStyle(ChatFormatting.GREEN);
        MutableComponent no = button("No", "/veinminer setup welcome no", Component.literal("Ignore for now"))
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

    private static MutableComponent button(String label, String command, Component hover) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> {
                    var result = style.withColor(ChatFormatting.AQUA);
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

    private static MutableComponent suggestButton(String label, String suggestion) {
        return Component.literal(label)
                .withStyle(style -> {
                    var result = style.withColor(ChatFormatting.AQUA).withUnderlined(true);
                    ClickEvent click = createClickEvent("SUGGEST_COMMAND", suggestion, "suggestCommand");
                    if (click != null) {
                        result = result.withClickEvent(click);
                    }
                    HoverEvent hover = createHoverEvent(Component.literal(suggestion));
                    if (hover != null) {
                        result = result.withHoverEvent(hover);
                    }
                    return result;
                });
    }

    @SuppressWarnings("unchecked")
    private static ClickEvent createClickEvent(String actionEnumName, String value, String factoryMethodName) {
        try {
            Class<?> actionClass = Class.forName("net.minecraft.network.chat.ClickEvent$Action");
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
    private static HoverEvent createHoverEvent(Component text) {
        try {
            Class<?> actionClass = Class.forName("net.minecraft.network.chat.HoverEvent$Action");
            if (actionClass.isEnum()) {
                Object action = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "SHOW_TEXT");
                for (var ctor : HoverEvent.class.getConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 2 && params[0].isAssignableFrom(actionClass) && params[1].isAssignableFrom(Component.class)) {
                        return (HoverEvent) ctor.newInstance(action, text);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            return (HoverEvent) HoverEvent.class.getMethod("showText", Component.class).invoke(null, text);
        } catch (Throwable ignored) {
        }

        return null;
    }
}
