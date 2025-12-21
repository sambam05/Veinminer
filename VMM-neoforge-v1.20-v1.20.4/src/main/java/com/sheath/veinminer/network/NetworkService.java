package com.sheath.veinminer.network;

import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.network.message.HandshakeMessage;
import com.sheath.veinminer.network.message.KeyStateMessage;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.state.KeyStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

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

    private void registerPayloadHandlers(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(ModConstants.MOD_ID)
                .versioned(PROTOCOL)
                .optional();

        registrar.play(HandshakeMessage.ID, HandshakeMessage::new, builder -> builder.server((payload, context) -> {
            context.workHandler().submitAsync(() -> context.player().ifPresent(player -> {
                if (player instanceof ServerPlayer serverPlayer) {
                    keyStates.registerClient(serverPlayer);
                }
            }));
        }));

        registrar.play(KeyStateMessage.ID, KeyStateMessage::new, builder -> builder.server((payload, context) -> {
            context.workHandler().submitAsync(() -> context.player().ifPresent(player -> {
                if (player instanceof ServerPlayer serverPlayer) {
                    handleKeyState(payload, serverPlayer);
                }
            }));
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
