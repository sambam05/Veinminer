package com.sheath.veinminer.player;

import net.minecraft.nbt.CompoundTag;

public interface PlayerSettingsDataHolder {
    CompoundTag veinminer$getPlayerData();

    void veinminer$setPlayerData(CompoundTag data);
}
