package net.fly.adrenaline.scheduler;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.config.AdrenalineConfig.AdaptiveWorkerMode;
import net.minecraft.server.MinecraftServer;

public final class AdaptiveWorldgenWorkers {

    private static final ScheduledExecutorService MONITOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Adrenaline worker monitor");
        thread.setDaemon(true);
        return thread;
    });
    private static final java.lang.management.OperatingSystemMXBean SYSTEM = ManagementFactory.getOperatingSystemMXBean();
    private static final AdaptiveWorkerLimit LIMIT = new AdaptiveWorkerLimit();
    private static volatile MinecraftServer server;
    private static volatile long tickStartedNanos;
    private static volatile long tickFinishedNanos;
    private static volatile double tickMillis;
    private static volatile long tickSequence;
    private static volatile boolean preparing;
    private static ScheduledFuture<?> monitoring;

    private AdaptiveWorldgenWorkers() {
    }

    public static synchronized void start(MinecraftServer candidate) {
        server = candidate;
        tickStartedNanos = 0L;
        tickFinishedNanos = 0L;
        tickMillis = 0.0D;
        tickSequence = 0L;
        preparing = true;
        refreshSettings();
        if (monitoring == null) {
            monitoring = MONITOR.scheduleAtFixedRate(AdaptiveWorldgenWorkers::sample, 1L, 1L, TimeUnit.SECONDS);
        }
    }

    public static synchronized void stop(MinecraftServer candidate) {
        if (server != candidate) {
            return;
        }
        server = null;
        if (monitoring != null) {
            monitoring.cancel(false);
            monitoring = null;
        }
        refreshSettings();
    }

    public static synchronized void refreshSettings() {
        int configured = AdrenalineConfig.resolvedGenerationWorkerThreads();
        LIMIT.reset(configured);
        ChunkJobScheduler.get().setWorkerLimit(configured);
    }

    public static void beginTick(MinecraftServer candidate) {
        if (server == candidate) {
            tickStartedNanos = System.nanoTime();
        }
    }

    public static void advanceTick(MinecraftServer candidate) {
        if (server == candidate) {
            preparing = false;
            tickSequence++;
        }
    }

    public static void advancePreparationTick(MinecraftServer candidate) {
        if (server == candidate && preparing) {
            tickSequence++;
        }
    }

    public static void endTick(MinecraftServer candidate) {
        if (server == candidate) {
            tickMillis = candidate.getAverageTickTime();
            tickFinishedNanos = System.nanoTime();
            tickStartedNanos = 0L;
        }
    }

    private static void sample() {
        MinecraftServer candidate = server;
        if (candidate == null) {
            return;
        }
        double cpuLoad = SYSTEM instanceof com.sun.management.OperatingSystemMXBean system ? system.getCpuLoad() : -1.0D;
        long now = System.nanoTime();
        long started = tickStartedNanos;
        double mspt = now - tickFinishedNanos < TimeUnit.SECONDS.toNanos(2L) ? tickMillis : 0.0D;
        if (started != 0L) {
            mspt = Math.max(mspt, (now - started) / 1_000_000.0D);
        }
        synchronized (AdaptiveWorldgenWorkers.class) {
            if (server != candidate) {
                return;
            }
            int configured = AdrenalineConfig.resolvedGenerationWorkerThreads();
            AdaptiveWorkerMode mode = AdrenalineConfig.get().adaptiveWorkerMode();
            boolean enabled = mode != AdaptiveWorkerMode.OFF && AdrenalineConfig.parallelWorldgenEnabled();
            ChunkJobScheduler.get().setWorkerLimit(LIMIT.update(configured, mode.minimumWorkers(configured), enabled, cpuLoad, mspt, tickSequence));
        }
    }
}
