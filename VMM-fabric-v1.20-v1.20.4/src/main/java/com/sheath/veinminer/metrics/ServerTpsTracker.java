package com.sheath.veinminer.metrics;

/**
 * Minimal TPS tracker used to adapt behaviour (e.g. dynamic block caps) to the
 * current server performance.
 */
public final class ServerTpsTracker {

    private static final int SAMPLE_SIZE = 100;
    private final long[] samples = new long[SAMPLE_SIZE];
    private int cursor = 0;
    private boolean warmedUp = false;

    public void recordTick(long nanoTime) {
        samples[cursor++ % SAMPLE_SIZE] = nanoTime;
        if (cursor >= SAMPLE_SIZE) {
            warmedUp = true;
        }
    }

    public double currentTps() {
        if (!warmedUp) {
            return 20.0d;
        }
        int target = (cursor - 1 + SAMPLE_SIZE) % SAMPLE_SIZE;
        long elapsed = System.nanoTime() - samples[target];
        if (elapsed <= 0L) {
            return 20.0d;
        }
        return SAMPLE_SIZE * 1_000_000_000.0 / elapsed;
    }

    public void reset() {
        cursor = 0;
        warmedUp = false;
    }
}

