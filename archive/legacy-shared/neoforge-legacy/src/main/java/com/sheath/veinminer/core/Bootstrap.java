package com.sheath.veinminer.core;

import com.sheath.veinminer.command.VeinMinerCommand;
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
import net.neoforged.bus.api.IEventBus;

/**
 * Initialises and exposes shared services for the mod. NeoForge listeners are
 * registered from the {@link com.sheath.veinminer.Veinminer} entrypoint.
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
        commonSetupComplete = true;
        Log.debug("Common bootstrap finished");
    }

    public void registerNetwork(IEventBus modEventBus) {
        networkService.register(modEventBus);
    }

    public synchronized void onClientSetup() {
        if (clientSetupComplete) {
            Log.debug("Client bootstrap already executed; skipping");
            return;
        }
        Log.debug("Bootstrapping client services");
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

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public ClearConfirmationManager confirmations() {
        return confirmations;
    }

    public NetworkService networkService() {
        return networkService;
    }

    public VeinMinerController controller() {
        return controller;
    }

    public VeinMinerCommand commandHandler() {
        return commandHandler;
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
}
