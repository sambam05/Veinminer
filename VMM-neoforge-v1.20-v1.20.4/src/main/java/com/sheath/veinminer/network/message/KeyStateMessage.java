package com.sheath.veinminer.network.message;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KeyStateMessage(boolean pressed) implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation(ModConstants.MOD_ID, "key_state");

    public KeyStateMessage(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(pressed);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
