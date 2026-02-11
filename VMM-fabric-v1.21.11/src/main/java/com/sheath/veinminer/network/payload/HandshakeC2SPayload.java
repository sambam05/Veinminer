package com.sheath.veinminer.network.payload;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HandshakeC2SPayload() implements CustomPayload {

    public static final Identifier ID_VALUE = Identifier.of(ModConstants.MOD_ID, "handshake");
    public static final CustomPayload.Id<HandshakeC2SPayload> ID = new CustomPayload.Id<>(ID_VALUE);
    public static final PacketCodec<PacketByteBuf, HandshakeC2SPayload> CODEC =
            PacketCodec.unit(new HandshakeC2SPayload());

    public HandshakeC2SPayload(PacketByteBuf ignored) {
        this();
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

