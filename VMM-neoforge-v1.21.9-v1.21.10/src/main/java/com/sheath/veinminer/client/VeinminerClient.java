package com.sheath.veinminer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.network.message.HandshakeMessage;
import com.sheath.veinminer.network.message.KeyStateMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class VeinminerClient {

    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "veinminer"));

    private static KeyMapping activateKey;
    private static boolean lastState;

    private VeinminerClient() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VeinminerClient::onClientSetup);
        modEventBus.addListener(VeinminerClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onPlayerLogin);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        // nothing required at the moment
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        activateKey = new KeyMapping("key.veinminer.activate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY);
        event.register(activateKey);
    }

    public static final class ClientEvents {

        public static void onClientTick(ClientTickEvent.Post event) {
            if (activateKey == null) {
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            boolean pressed = activateKey.isDown();
            if (pressed != lastState) {
                lastState = pressed;
                sendToServer(new KeyStateMessage(pressed));
            }
        }

        public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            sendToServer(HandshakeMessage.INSTANCE);
        }
    }

    private static void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
