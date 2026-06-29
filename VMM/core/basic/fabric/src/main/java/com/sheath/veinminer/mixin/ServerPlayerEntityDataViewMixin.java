package com.sheath.veinminer.mixin;

import com.sheath.veinminer.player.PlayerSettingsDataHolder;
import com.sheath.veinminer.player.PlayerSettingsNbtCompat;
import com.sheath.veinminer.player.PlayerSettingsNbtKeys;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityDataViewMixin implements PlayerSettingsDataHolder {
    @Unique
    private NbtCompound veinminer$playerData = new NbtCompound();

    @Inject(
            method = "readCustomData(Lnet/minecraft/storage/ReadView;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void veinminer$readPlayerData(ReadView readView, CallbackInfo ci) {
        veinminer$playerData = PlayerSettingsNbtCompat.readCompoundFromReadView(readView, PlayerSettingsNbtKeys.ROOT).copy();
    }

    @Inject(
            method = "writeCustomData(Lnet/minecraft/storage/WriteView;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void veinminer$writePlayerData(WriteView writeView, CallbackInfo ci) {
        if (veinminer$playerData.isEmpty()) {
            return;
        }
        PlayerSettingsNbtCompat.writeCompoundToWriteView(writeView, PlayerSettingsNbtKeys.ROOT, veinminer$playerData);
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
