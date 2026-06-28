package com.sheath.veinminer.mixin;

import com.sheath.veinminer.player.PlayerSettingsDataHolder;
import com.sheath.veinminer.player.PlayerSettingsNbtCompat;
import com.sheath.veinminer.player.PlayerSettingsNbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDataMixin implements PlayerSettingsDataHolder {
    @Unique
    private CompoundTag veinminer$playerData = new CompoundTag();

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void veinminer$readPlayerData(CompoundTag tag, CallbackInfo ci) {
        veinminer$playerData = PlayerSettingsNbtCompat.getCompound(tag, PlayerSettingsNbtKeys.ROOT).copy();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void veinminer$writePlayerData(CompoundTag tag, CallbackInfo ci) {
        if (veinminer$playerData.isEmpty()) {
            return;
        }
        tag.put(PlayerSettingsNbtKeys.ROOT, veinminer$playerData.copy());
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void veinminer$copyPlayerData(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if (oldPlayer instanceof PlayerSettingsDataHolder holder) {
            veinminer$playerData = holder.veinminer$getPlayerData().copy();
        }
    }

    @Override
    public CompoundTag veinminer$getPlayerData() {
        return veinminer$playerData;
    }

    @Override
    public void veinminer$setPlayerData(CompoundTag data) {
        veinminer$playerData = data.copy();
    }
}
