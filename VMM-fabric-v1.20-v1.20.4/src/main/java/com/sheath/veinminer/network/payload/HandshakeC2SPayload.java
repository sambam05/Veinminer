package com.sheath.veinminer.network.payload;

import com.sheath.veinminer.core.ModConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Simple handshake trigger for older networking API (1.20-1.20.4).
 */
public final class HandshakeC2SPayload {

    public static final Identifier ID = Identifier.of(ModConstants.MOD_ID, "handshake");

    private HandshakeC2SPayload() {
    }

    public static PacketByteBuf empty() {
        return new PacketByteBuf(Unpooled.buffer());
    }
}
