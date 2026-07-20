package net.fly.adrenaline.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class WorldgenStageStats {

    private static final List<ChunkStatus> STATUSES = ChunkStatus.getStatusList();
    private static final AtomicLongArray TOTAL_NANOS = new AtomicLongArray(STATUSES.size());
    private static final AtomicLongArray SAMPLE_COUNTS = new AtomicLongArray(STATUSES.size());
    private static final AtomicLong EPOCH = new AtomicLong();
    private static volatile boolean enabled;

    private WorldgenStageStats() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        EPOCH.incrementAndGet();
        WorldgenStageStats.enabled = enabled;
        if (enabled) {
            for (int i = 0; i < STATUSES.size(); i++) {
                TOTAL_NANOS.set(i, 0L);
                SAMPLE_COUNTS.set(i, 0L);
            }
        }
    }

    public static <T> CompletableFuture<T> track(ChunkStatus status, ChunkPos pos, long startedNanos, CompletableFuture<T> future) {
        if (startedNanos == 0L) {
            return future;
        }

        int index = status.getIndex();
        long epoch = EPOCH.get();
        future.whenComplete((value, throwable) -> {
            if (enabled && EPOCH.get() == epoch) {
                TOTAL_NANOS.addAndGet(index, System.nanoTime() - startedNanos);
                SAMPLE_COUNTS.incrementAndGet(index);
            }
        });
        return future;
    }

    public static List<StageTiming> snapshot() {
        List<StageTiming> timings = new ArrayList<>(STATUSES.size());
        for (ChunkStatus status : STATUSES) {
            int index = status.getIndex();
            long samples = SAMPLE_COUNTS.get(index);
            long averageNanos = samples == 0L ? 0L : TOTAL_NANOS.get(index) / samples;
            String name = status.toString();
            int separator = name.indexOf(':');
            timings.add(new StageTiming(name.substring(separator + 1).toUpperCase(Locale.ROOT), averageNanos));
        }
        Collections.reverse(timings);
        return timings;
    }

    public record StageTiming(String name, long averageNanos) {
    }
}
