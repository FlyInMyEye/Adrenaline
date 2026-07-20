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
    private static final NoiseSubstage[] NOISE_SUBSTAGES = NoiseSubstage.values();
    private static final AtomicLongArray NOISE_TOTAL_NANOS = new AtomicLongArray(NOISE_SUBSTAGES.length);
    private static final AtomicLongArray NOISE_SAMPLE_COUNTS = new AtomicLongArray(NOISE_SUBSTAGES.length);
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
            for (int i = 0; i < NOISE_SUBSTAGES.length; i++) {
                NOISE_TOTAL_NANOS.set(i, 0L);
                NOISE_SAMPLE_COUNTS.set(i, 0L);
            }
        }
    }

    public static NoiseProfile beginNoiseProfile() {
        return enabled ? new NoiseProfile(EPOCH.get()) : null;
    }

    public static void finishNoiseProfile(NoiseProfile profile) {
        if (profile == null || !enabled || EPOCH.get() != profile.epoch) {
            return;
        }
        for (int i = 0; i < NOISE_SUBSTAGES.length; i++) {
            NOISE_TOTAL_NANOS.addAndGet(i, profile.elapsedNanos[i]);
            NOISE_SAMPLE_COUNTS.incrementAndGet(i);
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
        List<StageTiming> timings = new ArrayList<>(STATUSES.size() + NOISE_SUBSTAGES.length);
        for (ChunkStatus status : STATUSES) {
            int index = status.getIndex();
            long samples = SAMPLE_COUNTS.get(index);
            long averageNanos = samples == 0L ? 0L : TOTAL_NANOS.get(index) / samples;
            String name = status.toString();
            int separator = name.indexOf(':');
            timings.add(new StageTiming(name.substring(separator + 1).toUpperCase(Locale.ROOT), averageNanos));
        }
        Collections.reverse(timings);
        int noiseIndex = -1;
        for (int i = 0; i < timings.size(); i++) {
            if ("NOISE".equals(timings.get(i).name())) {
                noiseIndex = i;
                break;
            }
        }
        if (noiseIndex >= 0) {
            List<StageTiming> noiseTimings = new ArrayList<>(NOISE_SUBSTAGES.length);
            for (NoiseSubstage substage : NOISE_SUBSTAGES) {
                int index = substage.ordinal();
                long samples = NOISE_SAMPLE_COUNTS.get(index);
                long averageNanos = samples == 0L ? 0L : NOISE_TOTAL_NANOS.get(index) / samples;
                noiseTimings.add(new StageTiming("  " + substage.displayName, averageNanos));
            }
            timings.addAll(noiseIndex + 1, noiseTimings);
        }
        return timings;
    }

    public enum NoiseSubstage {
        SETUP("SETUP"),
        SLICE_SAMPLING("SLICE SAMPLING"),
        CELL_CACHE("CELL CACHE"),
        INTERPOLATION("INTERPOLATION"),
        BLOCK_STATE("BLOCK STATE"),
        BLOCK_WRITE("BLOCK WRITE"),
        FINALIZE("FINALIZE");

        private final String displayName;

        NoiseSubstage(String displayName) {
            this.displayName = displayName;
        }
    }

    public static final class NoiseProfile {
        private final long epoch;
        private final long[] elapsedNanos = new long[NOISE_SUBSTAGES.length];

        private NoiseProfile(long epoch) {
            this.epoch = epoch;
        }

        public void add(NoiseSubstage substage, long elapsedNanos) {
            this.elapsedNanos[substage.ordinal()] += elapsedNanos;
        }
    }

    public record StageTiming(String name, long averageNanos) {
    }
}
