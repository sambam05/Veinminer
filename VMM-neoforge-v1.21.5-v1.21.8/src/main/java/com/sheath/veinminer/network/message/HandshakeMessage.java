package com.sheath.veinminer.network.message;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandshakeMessage() implements CustomPacketPayload {

    public static final Type<HandshakeMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "handshake"));
    public static final HandshakeMessage INSTANCE = new HandshakeMessage();
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeMessage> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
