package com.sheath.veinminer.network.payload;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KeyStateC2SPayload(boolean pressed) implements CustomPayload {

    public static final Identifier ID_VALUE = Identifier.of(ModConstants.MOD_ID, "key_state");
    public static final CustomPayload.Id<KeyStateC2SPayload> ID = new CustomPayload.Id<>(ID_VALUE);
    public static final PacketCodec<PacketByteBuf, KeyStateC2SPayload> CODEC =
            PacketCodec.of(KeyStateC2SPayload::write, KeyStateC2SPayload::new);

    public KeyStateC2SPayload(PacketByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBoolean(pressed);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

