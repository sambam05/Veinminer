package com.sheath.veinminer;

import com.sheath.veinminer.core.Bootstrap;
import com.sheath.veinminer.core.ModConstants;
import com.sheath.veinminer.util.Log;
import com.sheath.veinminer.command.SetupWelcomePrompt;
import com.sheath.veinminer.visual.ParticleOutlineManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mod(ModConstants.MOD_ID)
public final class Veinminer {

    public static final Bootstrap BOOTSTRAP = new Bootstrap();

    public Veinminer(IEventBus modEventBus) {
        Log.info("Starting {} (id={})", ModConstants.MOD_NAME, ModConstants.MOD_ID);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
        if (isClientDist()) {
            registerClientHooks(modEventBus);
        }
        BOOTSTRAP.registerNetwork(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }

    private void registerClientHooks(IEventBus modEventBus) {
        try {
            Class.forName("com.sheath.veinminer.client.VeinminerClient", false, Veinminer.class.getClassLoader())
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modEventBus);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to register client listeners", ex);
        }
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BOOTSTRAP.onCommonSetup();
            Log.info("{} common setup complete", ModConstants.MOD_NAME);
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(BOOTSTRAP::onClientSetup);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BOOTSTRAP.commandHandler().register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        BOOTSTRAP.controller().onServerStarted();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BOOTSTRAP.controller().onServerStopping();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        BOOTSTRAP.controller().onServerTick(event.getServer());
        BOOTSTRAP.confirmations().tick(event.getServer());
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = resolveServerLevel(event);
        if (level != null) {
            ParticleOutlineManager.onWorldTick(level);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ParticleOutlineManager.onWorldUnload(level);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean allowed = BOOTSTRAP.controller().handleBlockBreak(level, player, event.getPos(), event.getState());
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BOOTSTRAP.controller().onPlayerDisconnect(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SetupWelcomePrompt.maybeSend(BOOTSTRAP, player);
        }
    }

    private ServerLevel resolveServerLevel(TickEvent.LevelTickEvent event) {
        try {
            Object value = event.getClass().getMethod("getLevel").invoke(event);
            if (value instanceof ServerLevel level) {
                return level;
            }
        } catch (Exception ignored) {
        }
        try {
            Object value = event.getClass().getMethod("level").invoke(event);
            if (value instanceof ServerLevel level) {
                return level;
            }
        } catch (Exception ignored) {
        }
        try {
            Field field = event.getClass().getDeclaredField("level");
            field.setAccessible(true);
            Object value = field.get(event);
            if (value instanceof ServerLevel level) {
                return level;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isClientDist() {
        try {
            Field distField = FMLEnvironment.class.getField("dist");
            Object value = distField.get(null);
            if (value instanceof Dist distValue) {
                return distValue.isClient();
            }
        } catch (Exception ignored) {
        }
        try {
            Method getter = FMLEnvironment.class.getMethod("getDist");
            Object value = getter.invoke(null);
            if (value instanceof Dist distValue) {
                return distValue.isClient();
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
