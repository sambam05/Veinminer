package com.sheath.veinminer.mixin;

import com.sheath.veinminer.player.PlayerSettingsDataHolder;
import com.sheath.veinminer.player.PlayerSettingsNbtCompat;
import com.sheath.veinminer.player.PlayerSettingsNbtKeys;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityDataNbtMixin implements PlayerSettingsDataHolder {
    @Unique
    private NbtCompound veinminer$playerData = new NbtCompound();

    @Inject(
            method = "readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void veinminer$readPlayerData(NbtCompound nbt, CallbackInfo ci) {
        veinminer$playerData = PlayerSettingsNbtCompat.getCompound(nbt, PlayerSettingsNbtKeys.ROOT).copy();
    }

    @Inject(
            method = "writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void veinminer$writePlayerData(NbtCompound nbt, CallbackInfo ci) {
        if (veinminer$playerData.isEmpty()) {
            return;
        }
        nbt.put(PlayerSettingsNbtKeys.ROOT, veinminer$playerData.copy());
    }

    @Override
    public NbtCompound veinminer$getPlayerData() {
        return veinminer$playerData;
    }

    @Override
    public void veinminer$setPlayerData(NbtCompound data) {
        veinminer$playerData = data.copy();
    }
}
