package com.sheath.veinminer.state;

import com.sheath.veinminer.util.Translations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tracks destructive commands that require an explicit confirmation step.
 */
public final class ClearConfirmationManager {

    private static final long TIMEOUT_MILLIS = 10_000L;

    private final Map<String, PendingAction> pending = new HashMap<>();

    public synchronized void request(CommandSourceStack source, Consumer<CommandSourceStack> action) {
        pending.put(keyFor(source), new PendingAction(source, action, System.currentTimeMillis() + TIMEOUT_MILLIS));
        source.sendSuccess(() -> Translations.translate("command.veinminer.confirm.prompt"), false);
    }

    public synchronized int confirm(CommandSourceStack source) {
        PendingAction action = pending.remove(keyFor(source));
        if (action == null) {
            source.sendFailure(Translations.translate("command.veinminer.confirm.none"));
            return 0;
        }
        if (action.isExpired()) {
            action.notifyExpired();
            return 0;
        }
        action.run(source);
        return 1;
    }

    public synchronized int cancel(CommandSourceStack source) {
        PendingAction action = pending.remove(keyFor(source));
        if (action == null) {
            source.sendFailure(Translations.translate("command.veinminer.confirm.none"));
            return 0;
        }
        action.notifyCancelled();
        return 1;
    }

    public synchronized void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingAction>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingAction> entry = iterator.next();
            if (entry.getValue().expiresAt <= now) {
                PendingAction action = entry.getValue();
                iterator.remove();
                action.notifyExpired();
            }
        }
    }

    private static String keyFor(CommandSourceStack source) {
        try {
            var player = source.getPlayer();
            if (player != null) {
                return "player:" + player.getUUID().toString();
            }
        } catch (Exception ignored) {
        }
        return "source:" + source.getTextName();
    }

    private static final class PendingAction {
        private final CommandSourceStack feedbackTarget;
        private final Consumer<CommandSourceStack> action;
        private final long expiresAt;

        PendingAction(CommandSourceStack feedbackTarget, Consumer<CommandSourceStack> action, long expiresAt) {
            this.feedbackTarget = feedbackTarget;
            this.action = action;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        void run(CommandSourceStack source) {
            action.accept(source);
        }

        void notifyExpired() {
            feedbackTarget.sendSuccess(() -> Translations.translate("command.veinminer.confirm.expired"), false);
        }

        void notifyCancelled() {
            feedbackTarget.sendSuccess(() -> Translations.translate("command.veinminer.confirm.cancelled"), false);
        }
    }
}
