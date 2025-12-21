package com.sheath.veinminer.network.payload;

import com.sheath.veinminer.core.ModConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Key state payload helpers for pre-1.20.5 networking.
 */
public final class KeyStateC2SPayload {

    public static final Identifier ID = Identifier.of(ModConstants.MOD_ID, "key_state");

    private KeyStateC2SPayload() {}

    public static PacketByteBuf buf(boolean pressed) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(pressed);
        return buf;
    }

    public static boolean read(PacketByteBuf buf) {
        return buf.readBoolean();
    }
}
