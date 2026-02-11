package com.sheath.veinminer.network;

import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.network.message.HandshakeMessage;
import com.sheath.veinminer.network.message.KeyStateMessage;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.state.KeyStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkService {

    private static final String PROTOCOL = "1";

    private final KeyStateRegistry keyStates;
    private final PlayerSettingsStore playerSettings;

    public NetworkService(KeyStateRegistry keyStates, PlayerSettingsStore playerSettings) {
        this.keyStates = keyStates;
        this.playerSettings = playerSettings;
    }

    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloadHandlers);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModConstants.MOD_ID)
                .versioned(PROTOCOL)
                .optional()
                .executesOn(HandlerThread.MAIN);

        registrar.playToServer(HandshakeMessage.TYPE, HandshakeMessage.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        keyStates.registerClient(player);
                        player.level().getServer().getCommands().sendCommands(player);
                    }
                }));

        registrar.playToServer(KeyStateMessage.TYPE, KeyStateMessage.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleKeyState(payload, player);
                    }
                }));
    }

    private void handleKeyState(KeyStateMessage message, ServerPlayer player) {
        boolean wasPressed = keyStates.setKeyState(player, message.pressed());
        if (!playerSettings.useKeybind(player)) {
            return;
        }
        if (playerSettings.keyToggleMode(player) && message.pressed() && !wasPressed) {
            playerSettings.flipKeyToggleState(player);
        }
    }
}








