package com.sheath.veinminer.testing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.player.PlayerSettingsStore.MessageType;
import com.sheath.veinminer.util.Log;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import com.sheath.veinminer.util.Translations;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**

 * Executes scripted in-game checks to validate core Veinminer commands.

 */

public final class FeatureTestHarness {

    private final Bootstrap bootstrap;

    private boolean running;

    private static final Method FIND_VEIN = lookupFindVein();

    private static final Method APPLY_PLAN = lookupApplyPlan();

    private static final Method COMPUTE_BLOCK_CAP = lookupComputeBlockCap();

    public FeatureTestHarness(Bootstrap bootstrap) {

        this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");

    }

    public boolean start(CommandSourceStack source) {

        if (!acquire()) {

            return false;

        }

        ServerPlayer player = null;

        PlayerSnapshot snapshot = null;

        PlayerSettingsStore store = bootstrap.playerSettings();

        boolean hadClientMod = false;

        try {

            if (!(source.getEntity() instanceof ServerPlayer serverPlayer)) {

                source.sendFailure(Translations.translate("command.veinminer.test.player_only"));

                Log.warn("Feature test aborted: non-player source '{}'", source.getTextName());

                return true;

            }

            player = serverPlayer;

            hadClientMod = bootstrap.keyStates().hasClient(player);

            bootstrap.keyStates().registerClient(player);

            snapshot = PlayerSnapshot.capture(store, player);

            source.sendSuccess(() -> Translations.translate("command.veinminer.test.starting"), false);

            source.sendSuccess(() -> Translations.translate("command.veinminer.test.running"), false);

            Log.info("Feature test harness invoked by {}", player.getName().getString());

            TestContext context = new TestContext(bootstrap, source, player, store);

            List<TestStep> steps = createSteps();

            List<TestResult> results = new ArrayList<>(steps.size());

            for (TestStep step : steps) {

                results.add(runStep(step, context));

            }

            long passed = results.stream().filter(TestResult::success).count();

            source.sendSuccess(() -> Translations.translate("command.veinminer.test.summary", passed, steps.size()), false);

            Log.info("Feature test harness completed with {}/{} successes", passed, steps.size());

            for (TestResult result : results) {

                if (result.success()) {

                    source.sendSuccess(() -> Translations.translate("command.veinminer.test.step.pass", result.name()), false);

                } else {

                    source.sendFailure(Translations.translate("command.veinminer.test.step.fail", result.name(), result.message()));

                    Log.warn("Feature test step '{}' failed: {}", result.name(), result.message());

                }

            }

            return true;

        } finally {

            if (snapshot != null && player != null) {

                snapshot.restore(store, player);

                store.saveAsync();

            }

            if (player != null) {

                if (!hadClientMod) {
                    bootstrap.keyStates().unregister(player);
                }

            }

            release();

        }

    }

    private synchronized boolean acquire() {

        if (running) {

            return false;

        }

        running = true;

        return true;

    }

    private synchronized void release() {

        running = false;

    }

    private List<TestStep> createSteps() {

        List<TestStep> steps = new ArrayList<>();

        steps.add(new TestStep("Veinminer Toggle", ctx -> {

            boolean before = ctx.store().isVeinminerEnabled(ctx.player());

            ctx.expectCommandSuccess("veinminer toggle");

            boolean after = ctx.store().isVeinminerEnabled(ctx.player());

            ctx.assertEquals(!before, after, "Veinminer toggle did not invert player state");

        }));

        steps.add(new TestStep("Particle Toggle", ctx -> {

            boolean before = ctx.store().isParticlesEnabled(ctx.player());

            ctx.expectCommandSuccess("veinminer toggleparticles");

            boolean after = ctx.store().isParticlesEnabled(ctx.player());

            ctx.assertEquals(!before, after, "Particle toggle did not invert player state");

        }));

        steps.add(new TestStep("Permission Message Toggle", ctx -> {

            boolean before = ctx.store().isMessageEnabled(ctx.player(), MessageType.PERMISSION);

            ctx.expectCommandSuccess("veinminer togglemessages permission");

            boolean after = ctx.store().isMessageEnabled(ctx.player(), MessageType.PERMISSION);

            ctx.assertEquals(!before, after, "Message toggle did not invert permission message state");

        }));

        steps.add(new TestStep("Keybind Toggle Off", ctx -> {

            PlayerSettingsStore store = ctx.store();

            store.setUseKeybind(ctx.player(), true);

            store.resetKeyToggleState(ctx.player());

            ctx.expectCommandSuccess("veinminer activation keybind");

            ctx.assertTrue(!store.useKeybind(ctx.player()), "Keybind toggle did not disable keybind usage");

        }));

        steps.add(new TestStep("Keybind Toggle On", ctx -> {

            PlayerSettingsStore store = ctx.store();

            store.setUseKeybind(ctx.player(), false);

            store.resetKeyToggleState(ctx.player());

            ctx.expectCommandSuccess("veinminer activation keybind");

            ctx.assertTrue(store.useKeybind(ctx.player()), "Keybind toggle did not enable keybind usage");

        }));

        steps.add(new TestStep("Activation Mode Toggle", ctx -> {

            ctx.expectCommandSuccess("veinminer activation mode toggle");

            ctx.assertTrue(ctx.store().keyToggleMode(ctx.player()), "Activation mode toggle did not enable key toggle");

            ctx.assertTrue(ctx.store().crouchToggleMode(ctx.player()), "Activation mode toggle did not enable crouch toggle");

        }));

        steps.add(new TestStep("Activation Mode Hold", ctx -> {

            ctx.expectCommandSuccess("veinminer activation mode hold");

            ctx.assertTrue(!ctx.store().keyToggleMode(ctx.player()), "Activation mode hold did not switch keybind to hold");

            ctx.assertTrue(!ctx.store().crouchToggleMode(ctx.player()), "Activation mode hold did not switch crouch to hold");

        }));

        steps.add(new TestStep("World Veinminer Simulation", this::runWorldSimulation));

        steps.add(new TestStep("Config Reload", ctx -> ctx.expectCommandSuccess("veinminer reload")));

        return steps;

    }

    private void runWorldSimulation(TestContext ctx) throws Exception {

        VeinMinerController controller = bootstrap.controller();

        ConfigService.ConfigSnapshot snapshot = bootstrap.configService().snapshot();

        ServerLevel level = (ServerLevel) ctx.player().level();

        BlockPos origin = ctx.player().blockPosition().above();

        List<BlockPos> cluster = List.of(

                origin,

                origin.east(),

                origin.above()

        );

        Map<BlockPos, BlockState> originalStates = new HashMap<>();

        for (BlockPos pos : cluster) {

            originalStates.put(pos, level.getBlockState(pos));

            level.setBlock(pos, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_ALL);

        }

        boolean originalSneaking = ctx.player().isShiftKeyDown();

        ItemStack originalMainHand = ctx.player().getMainHandItem().copy();

        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);

        ctx.player().setItemInHand(InteractionHand.MAIN_HAND, tool);

        ctx.player().setShiftKeyDown(snapshot.general().requireCrouch());

        PlayerSettingsStore store = ctx.store();

        store.setVeinminerEnabled(ctx.player(), true);

        store.setParticlesEnabled(ctx.player(), false);

        store.setUseKeybind(ctx.player(), false);

        store.setKeyToggleMode(ctx.player(), false);

        store.resetKeyToggleState(ctx.player());

        store.setCrouchToggleMode(ctx.player(), false);

        store.setCrouchToggleState(ctx.player(), snapshot.general().requireCrouch());

        store.setLastCrouchInput(ctx.player(), snapshot.general().requireCrouch());

        try {

            int reserve = snapshot.general().durabilityReserveFor(tool.getMaxDamage());

            int remaining = tool.getMaxDamage() - tool.getDamageValue();

            int limit = (int) COMPUTE_BLOCK_CAP.invoke(controller, tool, remaining, reserve);

            Object plan = FIND_VEIN.invoke(controller,

                    level,

                    origin,

                    level.getBlockState(origin),

                    limit,

                    0,

                    tool.copy());

            APPLY_PLAN.invoke(controller, level, ctx.player(), tool, plan, reserve);

            for (BlockPos pos : cluster) {

                ctx.assertTrue(level.isEmptyBlock(pos), "Expected block at " + pos + " to be mined");

            }

            clearEntities(level, cluster);

        } finally {

            ctx.player().setItemInHand(InteractionHand.MAIN_HAND, originalMainHand);

            ctx.player().setShiftKeyDown(originalSneaking);

            for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {

                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);

            }

            clearEntities(level, cluster);

        }

    }

    private void clearEntities(ServerLevel level, List<BlockPos> cluster) {

        AABB area = new AABB(cluster.get(0)).inflate(2.0);

        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {

            item.discard();

        }

        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {

            orb.discard();

        }

    }

    private TestResult runStep(TestStep step, TestContext context) {

        try {

            step.action().run(context);

            return TestResult.success(step.name());

        } catch (AssertionError ex) {

            return TestResult.failure(step.name(), ex.getMessage());

        } catch (CommandSyntaxException ex) {

            return TestResult.failure(step.name(), ex.getMessage());

        } catch (Exception ex) {

            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

            return TestResult.failure(step.name(), message);

        }

    }

    private record TestStep(String name, TestAction action) { }

    @FunctionalInterface

    private interface TestAction {

        void run(TestContext context) throws Exception;

    }

    private record TestResult(String name, boolean success, String message) {

        static TestResult success(String name) {

            return new TestResult(name, true, "");

        }

        static TestResult failure(String name, String message) {

            return new TestResult(name, false, message);

        }

    }

    private static final class TestContext {

        private final Bootstrap bootstrap;

        private final CommandSourceStack source;

        private final ServerPlayer player;

        private final PlayerSettingsStore store;

        private final CommandDispatcher<CommandSourceStack> dispatcher;

        TestContext(Bootstrap bootstrap, CommandSourceStack source, ServerPlayer player, PlayerSettingsStore store) {

            this.bootstrap = bootstrap;

            this.source = source;

            this.player = player;

            this.store = store;

            this.dispatcher = source.getServer().getCommands().getDispatcher();

        }

        Bootstrap bootstrap() {

            return bootstrap;

        }

        ServerPlayer player() {

            return player;

        }

        PlayerSettingsStore store() {

            return store;

        }

        MinecraftServer server() {

            return source.getServer();

        }

        void expectCommandSuccess(String command) throws CommandSyntaxException {

            int result = dispatcher.execute(command, source);

            if (result <= 0) {

                throw new AssertionError("Command '" + command + "' completed with result " + result);

            }

        }

        void assertTrue(boolean condition, String message) {

            if (!condition) {

                throw new AssertionError(message);

            }

        }

        void assertEquals(boolean expected, boolean actual, String message) {

            if (expected != actual) {

                throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");

            }

        }

    }

    private static final class PlayerSnapshot {

        private final boolean veinminerEnabled;

        private final boolean particlesEnabled;

        private final Map<MessageType, Boolean> messages;

        private final boolean useKeybind;

        private final boolean keyToggleMode;

        private final boolean keyToggleActive;

        private final boolean crouchToggleMode;

        private final boolean crouchToggleActive;

        private final boolean lastCrouchInput;

        private PlayerSnapshot(boolean veinminerEnabled,

                               boolean particlesEnabled,

                               Map<MessageType, Boolean> messages,

                               boolean useKeybind,

                               boolean keyToggleMode,

                               boolean keyToggleActive,

                               boolean crouchToggleMode,

                               boolean crouchToggleActive,

                               boolean lastCrouchInput) {

            this.veinminerEnabled = veinminerEnabled;

            this.particlesEnabled = particlesEnabled;

            this.messages = messages;

            this.useKeybind = useKeybind;

            this.keyToggleMode = keyToggleMode;

            this.keyToggleActive = keyToggleActive;

            this.crouchToggleMode = crouchToggleMode;

            this.crouchToggleActive = crouchToggleActive;

            this.lastCrouchInput = lastCrouchInput;

        }

        static PlayerSnapshot capture(PlayerSettingsStore store, ServerPlayer player) {

            Map<MessageType, Boolean> messages = new EnumMap<>(MessageType.class);

            for (MessageType type : MessageType.values()) {

                messages.put(type, store.isMessageEnabled(player, type));

            }

            return new PlayerSnapshot(

                    store.isVeinminerEnabled(player),

                    store.isParticlesEnabled(player),

                    messages,

                    store.useKeybind(player),

                    store.keyToggleMode(player),

                    store.isKeyToggleActive(player),

                    store.crouchToggleMode(player),

                    store.isCrouchToggleActive(player),

                    store.lastCrouchInput(player)

            );

        }

        void restore(PlayerSettingsStore store, ServerPlayer player) {

            store.setVeinminerEnabled(player, veinminerEnabled);

            store.setParticlesEnabled(player, particlesEnabled);

            for (Map.Entry<MessageType, Boolean> entry : messages.entrySet()) {

                store.setMessageEnabled(player, entry.getKey(), entry.getValue());

            }

            store.setUseKeybind(player, useKeybind);

            store.setKeyToggleMode(player, keyToggleMode);

            store.setKeyToggleState(player, keyToggleActive);

            store.setCrouchToggleMode(player, crouchToggleMode);

            store.setCrouchToggleState(player, crouchToggleActive);

            store.setLastCrouchInput(player, lastCrouchInput);

        }

    }

    private static Method lookupFindVein() {

        try {

            Method method = VeinMinerController.class.getDeclaredMethod(

                    "findVein",

                    ServerLevel.class,

                    BlockPos.class,

                    BlockState.class,

                    int.class,

                    int.class,

                    ItemStack.class

            );

            method.setAccessible(true);

            return method;

        } catch (ReflectiveOperationException ex) {

            throw new IllegalStateException("Unable to access VeinMinerController#findVein", ex);

        }

    }

    private static Method lookupApplyPlan() {

        try {

            Method method = VeinMinerController.class.getDeclaredMethod(

                    "applyPlan",

                    ServerLevel.class,

                    ServerPlayer.class,

                    ItemStack.class,

                    FIND_VEIN.getReturnType(),

                    int.class

            );

            method.setAccessible(true);

            return method;

        } catch (ReflectiveOperationException ex) {

            throw new IllegalStateException("Unable to access VeinMinerController#applyPlan", ex);

        }

    }

    private static Method lookupComputeBlockCap() {

        try {

            Method method = VeinMinerController.class.getDeclaredMethod(

                    "computeBlockCap",

                    ItemStack.class,

                    int.class,

                    int.class

            );

            method.setAccessible(true);

            return method;

        } catch (ReflectiveOperationException ex) {

            throw new IllegalStateException("Unable to access VeinMinerController#computeBlockCap", ex);

        }

    }

}
