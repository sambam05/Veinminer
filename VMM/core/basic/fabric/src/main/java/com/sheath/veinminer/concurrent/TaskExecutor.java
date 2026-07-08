package com.sheath.veinminer.concurrent;

import com.sheath.veinminer.util.Log;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Thin wrapper around an {@link ExecutorService} that centralises thread naming
 * and lifecycle management.
 */
public final class TaskExecutor {

    private ExecutorService executor;
    private int configuredThreads = 0;

    public synchronized void configure(int threads) {
        int normalized = Math.max(1, threads);
        if (executor != null && !executor.isShutdown() && configuredThreads == normalized) {
            return;
        }
        shutdown();
        executor = Executors.newFixedThreadPool(normalized, new WorkerFactory());
        configuredThreads = normalized;
        Log.info("Initialised worker pool with {} threads", normalized);
    }

    public ExecutorService executor() {
        ExecutorService current = executor;
        if (current == null || current.isShutdown()) {
            throw new IllegalStateException("Task executor not configured");
        }
        return current;
    }

    public CompletableFuture<Void> submitAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.runAsync(task, executor());
    }

    public <T> CompletableFuture<T> submitAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return CompletableFuture.supplyAsync(supplier, executor());
    }

    public synchronized void shutdown() {
        if (executor == null) {
            return;
        }
        ExecutorService toShutdown = executor;
        executor = null;
        configuredThreads = 0;
        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(10, TimeUnit.SECONDS)) {
                Log.warn("Worker pool did not terminate gracefully; forcing shutdown");
                toShutdown.shutdownNow();
                if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.error("Worker pool still running after forced shutdown");
                }
            }
        } catch (InterruptedException ex) {
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class WorkerFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("veinminer-worker-" + sequence.getAndIncrement());
            thread.setUncaughtExceptionHandler((t, throwable) ->
                    Log.error("Uncaught exception in " + t.getName(), throwable));
            return thread;
        }
    }
}
