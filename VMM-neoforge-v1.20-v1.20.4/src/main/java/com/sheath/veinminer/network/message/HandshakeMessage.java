package com.sheath.veinminer.network.message;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandshakeMessage() implements CustomPacketPayload {

    public static final HandshakeMessage INSTANCE = new HandshakeMessage();
    public static final ResourceLocation ID = new ResourceLocation(ModConstants.MOD_ID, "handshake");

    public HandshakeMessage(FriendlyByteBuf buf) {
        this();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // nothing to write
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
