package com.sheath.veinminer.player;

import net.minecraft.nbt.NbtCompound;

public interface PlayerSettingsDataHolder {
    NbtCompound veinminer$getPlayerData();

    void veinminer$setPlayerData(NbtCompound data);
}
