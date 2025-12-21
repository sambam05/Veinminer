package com.sheath.veinminer.visual;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ParticleOutlineManager {

    private static final int SPAWN_INTERVAL_TICKS = 5;
    private static final double[][] EDGE_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {1, 0, 1},
            {0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {1, 1, 1},
            {0, 0, 0}, {0, 1, 0}, {1, 0, 0}, {1, 1, 0},
            {0, 0, 1}, {0, 1, 1}, {1, 0, 1}, {1, 1, 1}
    };

    private static final List<ParticleTask> TASKS = new ArrayList<>();
    private static boolean registered = false;

    private ParticleOutlineManager() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ServerTickEvents.END_WORLD_TICK.register(ParticleOutlineManager::onWorldTick);
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            synchronized (TASKS) {
                TASKS.removeIf(task -> task.world == world);
            }
        });
    }

    public static void spawnOutline(ServerWorld world, BlockPos pos, int red, int green, int blue, int durationTicks) {
        DustParticleEffect effect = new DustParticleEffect(packColor(red, green, blue), 1.0f);
        synchronized (TASKS) {
            TASKS.removeIf(task -> task.world == world && task.pos.equals(pos));
            TASKS.add(new ParticleTask(world, pos.toImmutable(), effect, durationTicks));
        }
    }

    private static void onWorldTick(ServerWorld world) {
        synchronized (TASKS) {
            Iterator<ParticleTask> iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                ParticleTask task = iterator.next();
                if (task.world != world) {
                    continue;
                }
                task.remaining--;
                task.cooldown--;
                if (task.cooldown <= 0) {
                    spawnEdges(task.world, task.pos, task.effect);
                    task.cooldown = SPAWN_INTERVAL_TICKS;
                }
                if (task.remaining <= 0) {
                    iterator.remove();
                }
            }
        }
    }

    private static void spawnEdges(ServerWorld world, BlockPos pos, DustParticleEffect effect) {
        for (double[] offset : EDGE_OFFSETS) {
            double x = pos.getX() + offset[0];
            double y = pos.getY() + offset[1];
            double z = pos.getZ() + offset[2];
            world.spawnParticles(effect, x, y, z, 1, 0, 0, 0, 0.01);
        }
    }

    private static int packColor(int r, int g, int b) {
        int clampedR = Math.max(0, Math.min(255, r));
        int clampedG = Math.max(0, Math.min(255, g));
        int clampedB = Math.max(0, Math.min(255, b));
        return (clampedR << 16) | (clampedG << 8) | clampedB;
    }

    private static final class ParticleTask {
        final ServerWorld world;
        final BlockPos pos;
        final DustParticleEffect effect;
        int remaining;
        int cooldown = 0;

        ParticleTask(ServerWorld world, BlockPos pos, DustParticleEffect effect, int duration) {
            this.world = world;
            this.pos = pos;
            this.effect = effect;
            this.remaining = duration;
        }
    }
}

