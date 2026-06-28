package com.sheath.veinminer.core;

import com.sheath.veinminer.command.VeinMinerCommand;
import com.sheath.veinminer.command.SetupWelcomePrompt;
import com.sheath.veinminer.concurrent.TaskExecutor;
import com.sheath.veinminer.config.ConfigService;
import com.sheath.veinminer.logic.VeinMinerController;
import com.sheath.veinminer.metrics.ServerTpsTracker;
import com.sheath.veinminer.network.NetworkService;
import com.sheath.veinminer.permission.PermissionService;
import com.sheath.veinminer.player.PlayerSettingsStore;
import com.sheath.veinminer.state.CooldownTracker;
import com.sheath.veinminer.state.KeyStateRegistry;
import com.sheath.veinminer.state.ClearConfirmationManager;
import com.sheath.veinminer.testing.FeatureTestHarness;
import com.sheath.veinminer.util.Log;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Coordinates high level setup for the mod. The real work will be performed by
 * specialised services (config, networking, gameplay logic) that are wired in
 * subsequent rebuild steps.
 */
public final class Bootstrap {

    private final ConfigService configService = new ConfigService();
    private final TaskExecutor taskExecutor = new TaskExecutor();
    private final PlayerSettingsStore playerSettings = new PlayerSettingsStore(taskExecutor);
    private final ServerTpsTracker tpsTracker = new ServerTpsTracker();
    private final PermissionService permissionService = new PermissionService();
    private final KeyStateRegistry keyStates = new KeyStateRegistry();
    private final CooldownTracker cooldowns = new CooldownTracker();
    private final NetworkService networkService = new NetworkService(keyStates, playerSettings);
    private final ClearConfirmationManager confirmations = new ClearConfirmationManager();
    private final VeinMinerController controller = new VeinMinerController(
            configService,
            taskExecutor,
            playerSettings,
            tpsTracker,
            permissionService,
            keyStates,
            cooldowns
    );
    private final VeinMinerCommand commandHandler = new VeinMinerCommand(this);
    private FeatureTestHarness featureTestHarness;

    private boolean commonSetupComplete;
    private boolean clientSetupComplete;
    private boolean eventsRegistered;

    /**
     * Called from {@link com.sheath.veinminer.Veinminer} during the Fabric common
     * entrypoint. The method is idempotent so reloads (e.g. during tests) won't
     * repeatedly wire listeners.
     */
    public synchronized void onCommonSetup() {
        if (commonSetupComplete) {
            Log.debug("Common bootstrap already executed; skipping");
            return;
        }
        Log.debug("Bootstrapping common services");
        configService.loadAll();
        playerSettings.load();
        controller.reloadFromConfig();
        taskExecutor.configure(configService.general().threadCount());
        registerEvents();
        commonSetupComplete = true;
        Log.debug("Common bootstrap finished");
    }

    /**
     * Client-specific bootstrap invoked from the client entrypoint. This too is
     * idempotent to play nicely with resource reloads.
     */
    public synchronized void onClientSetup() {
        if (clientSetupComplete) {
            Log.debug("Client bootstrap already executed; skipping");
            return;
        }
        Log.debug("Bootstrapping client services");
        // Future steps: register key bindings, renderers, etc.
        clientSetupComplete = true;
    }

    public ConfigService configService() {
        return configService;
    }

    public TaskExecutor taskExecutor() {
        return taskExecutor;
    }

    public PlayerSettingsStore playerSettings() {
        return playerSettings;
    }

    public ServerTpsTracker tpsTracker() {
        return tpsTracker;
    }

    public PermissionService permissionService() {
        return permissionService;
    }

    public KeyStateRegistry keyStates() {
        return keyStates;
    }

    public ClearConfirmationManager confirmations() {
        return confirmations;
    }

    public VeinMinerController controller() {
        return controller;
    }

    public FeatureTestHarness featureTestHarness() {
        if (featureTestHarness == null) {
            featureTestHarness = new FeatureTestHarness(this);
        }
        return featureTestHarness;
    }

    public boolean isCommonSetupComplete() {
        return commonSetupComplete;
    }

    public boolean isClientSetupComplete() {
        return clientSetupComplete;
    }

    private void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;

        networkService.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> controller.onServerStarted());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> controller.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            controller.onServerTick(server);
            confirmations.tick(server);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }
            return controller.handleBlockBreak(serverWorld, serverPlayer, pos, state);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SetupWelcomePrompt.maybeSend(this, handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                controller.onPlayerDisconnect(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                commandHandler.register(dispatcher));
    }
}
