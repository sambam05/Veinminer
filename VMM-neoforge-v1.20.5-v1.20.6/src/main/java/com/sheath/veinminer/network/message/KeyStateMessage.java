package com.sheath.veinminer.network.message;

import com.sheath.veinminer.core.ModConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KeyStateMessage(boolean pressed) implements CustomPacketPayload {

    public static final Type<KeyStateMessage> TYPE = new Type<>(new ResourceLocation(ModConstants.MOD_ID, "key_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyStateMessage> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, KeyStateMessage::pressed, KeyStateMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
