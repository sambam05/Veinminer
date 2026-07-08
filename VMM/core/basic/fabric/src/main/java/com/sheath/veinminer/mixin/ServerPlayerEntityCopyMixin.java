package com.sheath.veinminer.mixin;

import com.sheath.veinminer.player.PlayerSettingsDataHolder;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityCopyMixin {
    @Inject(
            method = "copyFrom(Lnet/minecraft/server/network/ServerPlayerEntity;Z)V",
            at = @At("TAIL"),
            require = 0
    )
    private void veinminer$copyPlayerData(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        if (!(oldPlayer instanceof PlayerSettingsDataHolder source)) {
            return;
        }
        if (!((Object) this instanceof PlayerSettingsDataHolder target)) {
            return;
        }
        target.veinminer$setPlayerData(source.veinminer$getPlayerData().copy());
    }
}
