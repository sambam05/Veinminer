package com.sheath.veinminer.logic.rules;

import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.config.GeneralConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.TagKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuleIndex {

    private final GeneralConfig.BlockListMode blockListMode;
    private final BlockRules globalBlockRules;
    private final boolean allowEmptyHand;
    private final Set<ResourceLocation> allowedToolIds;
    private final Set<TagKey<Item>> allowedToolTags;
    private final Map<ResourceLocation, BlockRules> perToolIdRules;
    private final Map<TagKey<Item>, BlockRules> perToolTagRules;
    private final BlockRules handRules;

    private RuleIndex(GeneralConfig.BlockListMode blockListMode,
                      BlockRules globalBlockRules,
                      boolean allowEmptyHand,
                      Set<ResourceLocation> allowedToolIds,
                      Set<TagKey<Item>> allowedToolTags,
                      Map<ResourceLocation, BlockRules> perToolIdRules,
                      Map<TagKey<Item>, BlockRules> perToolTagRules,
                      BlockRules handRules) {
        this.blockListMode = blockListMode;
        this.globalBlockRules = globalBlockRules;
        this.allowEmptyHand = allowEmptyHand;
        this.allowedToolIds = allowedToolIds;
        this.allowedToolTags = allowedToolTags;
        this.perToolIdRules = perToolIdRules;
        this.perToolTagRules = perToolTagRules;
        this.handRules = handRules;
    }

    public static RuleIndex fromSnapshot(ConfigService.ConfigSnapshot snapshot) {
        ConfigService.RegistryList<Block> blocks = snapshot.allowedBlocks();
        ConfigService.RegistryList<Item> tools = snapshot.allowedTools();

        Map<ResourceLocation, BlockRules> perToolIds = new LinkedHashMap<>();
        Map<TagKey<Item>, BlockRules> perToolTags = new LinkedHashMap<>();

        for (var entry : snapshot.blocksPerTool().byToolId().entrySet()) {
            perToolIds.put(entry.getKey(), BlockRules.from(entry.getValue()));
        }
        for (var entry : snapshot.blocksPerTool().byToolTag().entrySet()) {
            perToolTags.put(entry.getKey(), BlockRules.from(entry.getValue()));
        }
        ConfigService.RegistryList<Block> handList = snapshot.blocksPerTool().handRules();
        BlockRules handRules = handList != null ? BlockRules.from(handList) : null;

        return new RuleIndex(snapshot.general().blockListMode(),
                BlockRules.from(blocks),
                tools.allowEmptyHand(),
                Collections.unmodifiableSet(tools.identifiers()),
                Collections.unmodifiableSet(tools.tags()),
                Collections.unmodifiableMap(perToolIds),
                Collections.unmodifiableMap(perToolTags),
                handRules);
    }

    public boolean isToolAllowed(ItemStack tool) {
        if (tool.isEmpty()) {
            return allowEmptyHand;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(tool.getItem());
        if (allowedToolIds.contains(id)) {
            return true;
        }
        for (TagKey<Item> tag : allowedToolTags) {
            if (tool.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlockAllowed(BlockState state, ItemStack tool) {
        BlockRules rules = blockListMode.perTool() ? rulesFor(tool) : globalBlockRules;
        if (blockListMode.whitelist()) {
            return rules != null && rules.matches(state);
        }
        if (rules == null) {
            return true;
        }
        return !rules.matches(state);
    }

    public BlockRules rulesFor(ItemStack tool) {
        if (!blockListMode.perTool()) {
            return globalBlockRules;
        }
        if (tool.isEmpty()) {
            return handRules;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(tool.getItem());
        BlockRules rules = perToolIdRules.get(id);
        if (rules != null) {
            return rules;
        }
        for (TagKey<Item> tag : perToolTagRules.keySet()) {
            if (tool.is(tag)) {
                return perToolTagRules.get(tag);
            }
        }
        return null;
    }

    public GeneralConfig.BlockListMode blockListMode() {
        return blockListMode;
    }

    public BlockRules globalBlockRules() {
        return globalBlockRules;
    }

    public Map<ResourceLocation, BlockRules> perToolIdRules() {
        return perToolIdRules;
    }

    public Map<TagKey<Item>, BlockRules> perToolTagRules() {
        return perToolTagRules;
    }

    public boolean allowEmptyHand() {
        return allowEmptyHand;
    }

    public static final class BlockRules {
        private final Set<ResourceLocation> resourceLocations;
        private final Set<TagKey<Block>> tags;

        private BlockRules(Set<ResourceLocation> resourceLocations, Set<TagKey<Block>> tags) {
            this.resourceLocations = resourceLocations;
            this.tags = tags;
        }

        public static BlockRules from(ConfigService.RegistryList<Block> list) {
            Objects.requireNonNull(list, "registry list");
            return new BlockRules(Collections.unmodifiableSet(list.identifiers()),
                    Collections.unmodifiableSet(list.tags()));
        }

        public boolean matches(BlockState state) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (resourceLocations.contains(id)) {
                return true;
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        }

        public Set<ResourceLocation> resourceLocations() {
            return resourceLocations;
        }

        public Set<TagKey<Block>> tags() {
            return tags;
        }
    }
}
