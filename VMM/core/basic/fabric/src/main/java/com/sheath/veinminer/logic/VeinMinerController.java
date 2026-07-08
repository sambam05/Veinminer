package com.sheath.veinminer.logic;

import com.sheath.veinminer.concurrent.TaskExecutor;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.logic.rules.RuleIndex;
import com.sheath.veinminer.mixin.ExperienceDroppingBlockAccessor;
import com.sheath.veinminer.metrics.ServerTpsTracker;
import com.sheath.veinminer.permission.PermissionService;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.player.PlayerSettingsStore.MessageType;
import com.sheath.veinminer.state.CooldownTracker;
import com.sheath.veinminer.state.KeyStateRegistry;
import com.sheath.veinminer.util.Log;
import com.sheath.veinminer.visual.ParticleOutlineManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContext;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.IntProvider;
import com.sheath.veinminer.util.Translations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@SuppressWarnings({"deprecation", "unchecked", "rawtypes"})
public final class VeinMinerController {

    public static final class Permissions {
        public static final String USE = "veinminer.use";
        public static final String RELOAD = "veinminer.reload";
        public static final String SETTINGS_MANAGE = "veinminer.settings.manage";

        public static final String BLOCKS_MANAGE = "veinminer.blocks.manage";
        public static final String BLOCKS_ADD = "veinminer.blocks.add";
        public static final String BLOCKS_REMOVE = "veinminer.blocks.remove";
        public static final String BLOCKS_LIST = "veinminer.blocks.list";

        public static final String TOOLS_MANAGE = "veinminer.tools.manage";
        public static final String TOOLS_ADD = "veinminer.tools.add";
        public static final String TOOLS_REMOVE = "veinminer.tools.remove";
        public static final String TOOLS_LIST = "veinminer.tools.list";

        public static final String BLOCKPERTOOL_MANAGE = "veinminer.blockpertool.manage";
        public static final String BLOCKPERTOOL_BLOCKS_ADD = "veinminer.blockpertool.blocks.add";
        public static final String BLOCKPERTOOL_BLOCKS_REMOVE = "veinminer.blockpertool.blocks.remove";
        public static final String BLOCKPERTOOL_BLOCKS_LIST = "veinminer.blockpertool.blocks.list";
        public static final String BLOCKPERTOOL_TOOLS_ADD = "veinminer.blockpertool.tools.add";
        public static final String BLOCKPERTOOL_TOOLS_REMOVE = "veinminer.blockpertool.tools.remove";
        public static final String BLOCKPERTOOL_TOOLS_LIST = "veinminer.blockpertool.tools.list";

        public static final String PARTICLES_MANAGE = "veinminer.particles.manage";
        public static final String PARTICLES_ENABLE = "veinminer.particles.enable";
        public static final String PARTICLES_DISABLE = "veinminer.particles.disable";
        public static final String PARTICLES_SETCOLOR = "veinminer.particles.setcolor";
        public static final String PARTICLES_SETDURATION = "veinminer.particles.setduration";

        private Permissions() {}
    }

    private static final BlockPos[] NEIGHBOR_OFFSETS = buildNeighborOffsets();
    private static final float VANILLA_BLOCK_BREAK_EXHAUSTION = 0.005f;

    private final ConfigService configService;
    private final TaskExecutor taskExecutor;
    private final PlayerSettingsStore playerSettings;
    private final ServerTpsTracker tpsTracker;
    private final PermissionService permissionService;
    private final KeyStateRegistry keyStates;
    private final CooldownTracker cooldowns;

    private ConfigService.ConfigSnapshot snapshot;
    private RuleIndex ruleIndex;
    private long autosaveCounter = 0L;

    public VeinMinerController(ConfigService configService,
                               TaskExecutor taskExecutor,
                               PlayerSettingsStore playerSettings,
                               ServerTpsTracker tpsTracker,
                               PermissionService permissionService,
                               KeyStateRegistry keyStates,
                               CooldownTracker cooldowns) {
        this.configService = configService;
        this.taskExecutor = taskExecutor;
        this.playerSettings = playerSettings;
        this.tpsTracker = tpsTracker;
        this.permissionService = permissionService;
        this.keyStates = keyStates;
        this.cooldowns = cooldowns;
    }

    public void reloadFromConfig() {
        this.snapshot = configService.snapshot();
        this.ruleIndex = RuleIndex.fromSnapshot(snapshot);
        taskExecutor.configure(snapshot.general().threadCount());
        permissionService.configure(snapshot.general().autoLuckPerms());
    }

    public void onServerStarted() {
        ParticleOutlineManager.register();
        reloadFromConfig();
        autosaveCounter = 0L;
    }

    public void onServerStopping() {
        playerSettings.saveBlocking();
        cooldowns.clearAll();
        taskExecutor.shutdown();
    }

    public void onServerTick(MinecraftServer server) {
        tpsTracker.recordTick(System.nanoTime());
        autosaveCounter++;
        if (autosaveCounter >= 6000L) {
            autosaveCounter = 0L;
            playerSettings.saveAsync();
        }
    }

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        keyStates.unregister(player);
        cooldowns.clear(player);
        playerSettings.saveAndDrop(player);
    }

    public boolean handleBlockBreak(ServerWorld world,
                                    ServerPlayerEntity player,
                                    BlockPos pos,
                                    BlockState state) {
        if (snapshot == null || ruleIndex == null) {
            return true;
        }

        if (!snapshot.general().veinminerEnabled()) {
            return true;
        }

        if (!permissionService.hasPermission(player, Permissions.USE)) {
            notify(player, MessageType.PERMISSION, "message.veinminer.no_permission", false);
            return true;
        }

        if (!playerSettings.isVeinminerEnabled(player)) {
            notify(player, MessageType.DISABLED, "message.veinminer.disabled", true);
            return true;
        }

        if (!wantsVeinminer(player)) {
            return true;
        }

        ItemStack tool = player.getMainHandStack();

        if (!ruleIndex.isToolAllowed(tool)) {
            return true;
        }

        if (snapshot.general().requireCorrectTool()
                && !player.canHarvest(state)
                && state.isToolRequired()
                && !allowsIncorrectTool(state)) {
            return true;
        }
        if (!ruleIndex.isBlockAllowed(state, tool)) {
            return true;
        }

        if (snapshot.general().cooldown().enabled()) {
            int remainingSeconds = cooldowns.remainingSeconds(player, snapshot.general().cooldown().seconds());
            if (remainingSeconds > 0) {
                notify(player, MessageType.COOLDOWN, "message.veinminer.cooldown", false, remainingSeconds);
                return false;
            }
        }

        boolean creativeOrSpectator = isCreativeOrSpectator(player);
        int reserve = creativeOrSpectator ? 0 : snapshot.general().durabilityReserveFor(tool.getMaxDamage());
        int remaining = (creativeOrSpectator || !tool.isDamageable())
                ? Integer.MAX_VALUE
                : tool.getMaxDamage() - tool.getDamage();
        if (!creativeOrSpectator && snapshot.general().checkToolDurability() && tool.isDamageable() && remaining <= reserve) {
            notify(player, MessageType.DURABILITY, "message.veinminer.low_durability", true);
            return true;
        }

        int blockCap = computeBlockCap(tool, remaining, reserve, !creativeOrSpectator);
        if (blockCap <= 0) {
            return true;
        }

        int silkLevel = resolveSilkLevel(world, tool);
        ItemStack toolCopy = tool.copy();
        BlockState originState = state;
        Set<BlockPos> veinBlocks = findVein(world, pos, originState, blockCap);

        CompletableFuture<VeinPlan> future = taskExecutor.submitAsync(() ->
                createPlan(pos, originState, veinBlocks, silkLevel, toolCopy));

        future.thenAccept(plan -> world.getServer().execute(() ->
                        applyPlan(world, player, tool, plan, reserve)))
                .exceptionally(error -> {
                    world.getServer().execute(() -> {
                        Log.error("Failed to process async vein plan; applying single-block fallback", error);
                        VeinPlan fallback = createPlan(pos, originState, Set.of(pos), silkLevel, toolCopy);
                        applyPlan(world, player, tool, fallback, reserve);
                    });
                    return null;
                });

        return false;
    }

    private boolean wantsVeinminer(ServerPlayerEntity player) {
        if (playerSettings.useKeybind(player)) {
            if (!keyStates.hasClient(player)) {
                playerSettings.resetKeyToggleState(player);
                return false;
            }
            if (playerSettings.keyToggleMode(player)) {
                return playerSettings.isKeyToggleActive(player);
            }
            return keyStates.isKeyPressed(player);
        }
        playerSettings.resetKeyToggleState(player);
        return playerSettings.updateCrouchToggleState(player, player.isSneaking());
    }

    private int computeBlockCap(ItemStack tool, int remaining, int reserve, boolean applyDurabilityCap) {
        int baseLimit;
        var limits = snapshot.general().blockLimits();
        if (limits.dynamicMaxBlocks()) {
            double tps = tpsTracker.currentTps();
            int range = limits.maxDynamicBlocks() - limits.minBlocks();
            int computed = (int) Math.round((tps / 20.0) * range) + limits.minBlocks();
            baseLimit = Math.max(limits.minBlocks(), Math.min(limits.maxDynamicBlocks(), computed));
        } else {
            baseLimit = limits.maxBlocks();
        }

        if (!applyDurabilityCap || !snapshot.general().checkToolDurability() || !tool.isDamageable()) {
            return baseLimit;
        }
        return VeinMiningDurability.capBlockLimit(baseLimit, true, true, remaining, reserve);
    }

    private Set<BlockPos> findVein(ServerWorld world,
                                   BlockPos origin,
                                   BlockState originState,
                                   int limit) {

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        Block originBlock = originState.getBlock();

        while (!queue.isEmpty() && visited.size() < limit) {
            BlockPos current = queue.poll();
            for (BlockPos offset : NEIGHBOR_OFFSETS) {
                BlockPos neighbor = current.add(offset);
                if (visited.contains(neighbor)) continue;
                if (world.getBlockState(neighbor).getBlock() == originBlock) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    if (visited.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return visited;
    }

    private VeinPlan createPlan(BlockPos origin,
                                BlockState originState,
                                Set<BlockPos> blocks,
                                int silkLevel,
                                ItemStack toolCopy) {
        return new VeinPlan(origin, originState, new LinkedHashSet<>(blocks), silkLevel, toolCopy.copy());
    }

    private void applyPlan(ServerWorld world,
                           ServerPlayerEntity player,
                           ItemStack actualTool,
                           VeinPlan plan,
                           int reserve) {
        if (plan.blocks().isEmpty()) {
            return;
        }

        boolean creativeOrSpectator = isCreativeOrSpectator(player);
        boolean checkDurability = !creativeOrSpectator && snapshot.general().checkToolDurability() && actualTool.isDamageable();
        int remaining = checkDurability ? actualTool.getMaxDamage() - actualTool.getDamage() : Integer.MAX_VALUE;
        int limit = VeinMiningDurability.capBreakableBlocks(plan.blocks().size(),
                checkDurability, checkDurability, remaining, reserve);

        if (limit <= 0) {
            notify(player, MessageType.DURABILITY, "message.veinminer.low_durability", true);
            return;
        }

        int broken = 0;
        int totalXp = 0;
        List<ItemStack> collectedDrops = new ArrayList<>();

        for (BlockPos pos : plan.blocks()) {
            if (broken >= limit) {
                break;
            }
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            player.incrementStat(Stats.MINED.getOrCreateStat(state.getBlock()));

            if (playerSettings.isParticlesEnabled(player) && snapshot.general().particles().enabled()) {
                ParticleOutlineManager.spawnOutline(world, pos,
                        playerSettings.particleRed(player),
                        playerSettings.particleGreen(player),
                        playerSettings.particleBlue(player),
                        playerSettings.particleDurationTicks(player));
            }

            boolean skipSnowDrops = isSnowWithoutShovel(state, plan.toolCopy());
            if (!skipSnowDrops) {
                mergeDrops(collectedDrops, collectDrops(world, pos, state, plan.toolCopy(), plan.silkLevel(), player));

                if (plan.silkLevel() == 0 && state.getBlock() instanceof ExperienceDroppingBlock block) {
                    totalXp += resolveExperience(block, world);
                }
            }

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            broken++;
        }

        applyVeinminerExhaustion(player, broken);

        if (checkDurability && broken < plan.blocks().size()) {
            notify(player, MessageType.DURABILITY, "message.veinminer.limit_durability",
                    true, plan.blocks().size(), broken);
        }

        for (ItemStack drop : collectedDrops) {
            Block.dropStack(world, plan.origin(), drop);
        }

        if (totalXp > 0) {
            ExperienceOrbEntity.spawn(world, Vec3d.of(plan.origin()), totalXp);
        }

        world.playSound(null, plan.origin(), plan.originState().getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);

        if (!creativeOrSpectator && actualTool.isDamageable()) {
            damageTool(actualTool, broken, player);
        }

        if (snapshot.general().cooldown().enabled() && broken > 0) {
            cooldowns.startCooldown(player);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int resolveSilkLevel(ServerWorld world, ItemStack tool) {
        Object silkLookup = Enchantments.SILK_TOUCH;
        return extractSilkLevel(silkLookup, tool);
    }

    @SuppressWarnings("unchecked")
    private int extractSilkLevel(Object silkLookup, ItemStack tool) {
        Class<?> entryClass = RegistryEntry.class;
        Class<?> enchantmentClass = Enchantment.class;
        Class<?> itemClass = ItemStack.class;

        for (Method method : EnchantmentHelper.class.getMethods()) {
            if (!method.getName().equals("getLevel") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?> first = method.getParameterTypes()[0];
            Class<?> second = method.getParameterTypes()[1];
            try {
                if (entryClass.isAssignableFrom(first) && itemClass.isAssignableFrom(second) && silkLookup instanceof RegistryEntry<?> entry) {
                    return (int) method.invoke(null, entry, tool);
                }
                if (enchantmentClass.isAssignableFrom(first) && itemClass.isAssignableFrom(second)) {
                    Enchantment enchantment = silkLookup instanceof RegistryEntry<?>
                            ? ((RegistryEntry<Enchantment>) silkLookup).value()
                            : (Enchantment) silkLookup;
                    return (int) method.invoke(null, enchantment, tool);
                }
                if (itemClass.isAssignableFrom(first) && entryClass.isAssignableFrom(second) && silkLookup instanceof RegistryEntry<?> entry) {
                    return (int) method.invoke(null, tool, entry);
                }
                if (itemClass.isAssignableFrom(first) && enchantmentClass.isAssignableFrom(second)) {
                    Enchantment enchantment = silkLookup instanceof RegistryEntry<?>
                            ? ((RegistryEntry<Enchantment>) silkLookup).value()
                            : (Enchantment) silkLookup;
                    return (int) method.invoke(null, tool, enchantment);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        // Fallback: no compatible helper found; assume no silk touch
        return 0;
    }

    private List<ItemStack> collectDrops(ServerWorld world,
                                         BlockPos pos,
                                         BlockState state,
                                         ItemStack tool,
                                         int silkLevel,
                                         ServerPlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        try {
            return Block.getDroppedStacks(state, world, pos, blockEntity, player, tool);
        } catch (Exception ignored) {
        }
        try {
            return Block.getDroppedStacks(state, world, pos, blockEntity);
        } catch (Exception ignored) {
        }

        return List.of();
    }

    private void mergeDrops(List<ItemStack> target, List<ItemStack> newDrops) {
        outer:
        for (ItemStack drop : newDrops) {
            for (ItemStack existing : target) {
                if (stacksMatch(existing, drop)) {
                    existing.increment(drop.getCount());
                    continue outer;
                }
            }
            target.add(drop.copy());
        }
    }

    private int resolveExperience(ExperienceDroppingBlock block, ServerWorld world) {
        IntProvider provider = ((ExperienceDroppingBlockAccessor) block).veinminer$getExperienceDropped();
        return provider.get(world.random);
    }

    private void notify(ServerPlayerEntity player, MessageType type, String translationKey, boolean actionBar, Object... args) {
        if (snapshot != null && !snapshot.general().visual().showChatFeedback()) {
            return;
        }
        if (!playerSettings.isMessageEnabled(player, type)) {
            return;
        }
        Text message = Translations.translate(translationKey, args);
        player.sendMessage(message, actionBar);
    }

    private void applyVeinminerExhaustion(ServerPlayerEntity player, int brokenBlocks) {
        if (brokenBlocks <= 0 || snapshot == null) {
            return;
        }
        var exhaustion = snapshot.general().exhaustion();
        if (!exhaustion.enabled()) {
            return;
        }
        double scale = exhaustion.scale();
        if (scale <= 0.0) {
            return;
        }
        if (isCreativeOrSpectator(player)) {
            return;
        }
        float amount = (float) (VANILLA_BLOCK_BREAK_EXHAUSTION * scale * brokenBlocks);
        applyExhaustion(player, amount);
    }

    private boolean isCreativeOrSpectator(ServerPlayerEntity player) {
        if (invokeBoolean(player, "isSpectator")) {
            return true;
        }
        if (invokeBoolean(player, "isCreative")) {
            return true;
        }
        Object abilities = invokeNoArgs(player, "getAbilities");
        if (abilities != null) {
            Boolean creativeMode = readBooleanField(abilities, "creativeMode");
            if (creativeMode != null && creativeMode) {
                return true;
            }
            Boolean instabuild = readBooleanField(abilities, "instabuild");
            if (instabuild != null && instabuild) {
                return true;
            }
        }
        return false;
    }

    private void applyExhaustion(ServerPlayerEntity player, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        if (invokeExhaustion(player, "addExhaustion", amount)) {
            return;
        }
        invokeExhaustion(player, "causeFoodExhaustion", amount);
    }

    private boolean invokeExhaustion(Object target, String methodName, float amount) {
        try {
            Method method = target.getClass().getMethod(methodName, float.class);
            method.invoke(target, amount);
            return true;
        } catch (Exception ignored) {
        }
        try {
            Method method = target.getClass().getMethod(methodName, double.class);
            method.invoke(target, (double) amount);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean invokeBoolean(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean bool && bool;
        } catch (Exception ignored) {
        }
        return false;
    }

    private Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    private Boolean readBooleanField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return value instanceof Boolean bool ? bool : null;
        } catch (Exception ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Boolean bool ? bool : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void damageTool(ItemStack tool, int amount, ServerPlayerEntity player) {
        try {
            Method modern = ItemStack.class.getMethod("damage", int.class, ServerPlayerEntity.class, EquipmentSlot.class);
            modern.invoke(tool, amount, player, EquipmentSlot.MAINHAND);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method legacy = ItemStack.class.getMethod("damage", int.class, java.util.Random.class, ServerPlayerEntity.class);
            legacy.invoke(tool, amount, player.getRandom(), player);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method consumerDamage = ItemStack.class.getMethod("damage", int.class, LivingEntity.class, Consumer.class);
            consumerDamage.invoke(tool, amount, player, (Consumer<LivingEntity>) living -> {});
            return;
        } catch (Exception ignored) {
        }
        tool.setDamage(tool.getDamage() + amount);
    }

    private boolean stacksMatch(ItemStack a, ItemStack b) {
        try {
            Method canCombine = ItemStack.class.getMethod("canCombine", ItemStack.class, ItemStack.class);
            return (boolean) canCombine.invoke(null, a, b);
        } catch (Exception ignored) {
        }
        try {
            Method method = ItemStack.class.getMethod("areItemsAndComponentsEqual", ItemStack.class, ItemStack.class);
            return (boolean) method.invoke(null, a, b);
        } catch (Exception ignored) {
        }
        try {
            Method method = ItemStack.class.getMethod("areEqual", ItemStack.class, ItemStack.class);
            return (boolean) method.invoke(null, a, b);
        } catch (Exception ignored) {
        }
        return ItemStack.areItemsEqual(a, b);
    }

    
    private boolean allowsIncorrectTool(BlockState state) {
        return isSnow(state);
    }

    private boolean isSnow(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.SNOW || block == Blocks.SNOW_BLOCK;
    }

    private boolean isSnowWithoutShovel(BlockState state, ItemStack tool) {
        return isSnow(state) && !isShovel(tool);
    }

    private boolean isShovel(ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        var item = tool.getItem();
        return item == Items.WOODEN_SHOVEL
                || item == Items.STONE_SHOVEL
                || item == Items.IRON_SHOVEL
                || item == Items.GOLDEN_SHOVEL
                || item == Items.DIAMOND_SHOVEL
                || item == Items.NETHERITE_SHOVEL;
    }

    private static BlockPos[] buildNeighborOffsets() {
        BlockPos[] offsets = new BlockPos[26];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    offsets[index++] = new BlockPos(dx, dy, dz);
                }
            }
        }
        return offsets;
    }

    private record VeinPlan(BlockPos origin,
                            BlockState originState,
                            Set<BlockPos> blocks,
                            int silkLevel,
                            ItemStack toolCopy) {}
}
