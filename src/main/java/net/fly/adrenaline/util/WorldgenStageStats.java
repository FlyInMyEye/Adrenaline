package net.fly.adrenaline.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import net.fly.adrenaline.BuildConfig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class WorldgenStageStats {

    private static final List<ChunkStatus> STATUSES = ChunkStatus.getStatusList();
    private static final AtomicLongArray AVERAGE_NANOS = new AtomicLongArray(STATUSES.size());
    private static final NoiseSubstage[] NOISE_SUBSTAGES = NoiseSubstage.values();
    private static final AtomicLongArray NOISE_AVERAGE_NANOS = new AtomicLongArray(NOISE_SUBSTAGES.length);
    private static final ConcurrentHashMap<StageKey, SchedulingState> STAGE_SCHEDULING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ChunkScheduling> CHUNK_SCHEDULING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, AtomicLong> LAST_STAGE_FINISH_NANOS = new ConcurrentHashMap<>();
    private static final AtomicLong SCHEDULING_AVERAGE_NANOS = new AtomicLong();
    private static final AtomicLong WAITING_AVERAGE_NANOS = new AtomicLong();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static volatile boolean enabled;

    private WorldgenStageStats() {
    }

    public static boolean isEnabled() {
        if (!BuildConfig.DEBUG) {
            return false;
        }
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        EPOCH.incrementAndGet();
        WorldgenStageStats.enabled = enabled;
        if (enabled) {
            for (int i = 0; i < STATUSES.size(); i++) {
                AVERAGE_NANOS.set(i, 0L);
            }
            for (int i = 0; i < NOISE_SUBSTAGES.length; i++) {
                NOISE_AVERAGE_NANOS.set(i, 0L);
            }
            STAGE_SCHEDULING.clear();
            CHUNK_SCHEDULING.clear();
            LAST_STAGE_FINISH_NANOS.clear();
            SCHEDULING_AVERAGE_NANOS.set(0L);
            WAITING_AVERAGE_NANOS.set(0L);
        }
    }

    public static void beginScheduling(ChunkPos pos, ChunkStatus status) {
        if (!BuildConfig.DEBUG || !enabled) {
            return;
        }
        long now = System.nanoTime();
        STAGE_SCHEDULING.put(new StageKey(pos.toLong(), status.getIndex()), new SchedulingState(EPOCH.get(), now));
    }

    public static void finishInitialScheduling(ChunkPos pos, ChunkStatus status) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        SchedulingState state = STAGE_SCHEDULING.get(new StageKey(pos.toLong(), status.getIndex()));
        if (state == null || !enabled || EPOCH.get() != state.epoch) {
            return;
        }
        state.schedulingNanos.addAndGet(System.nanoTime() - state.startedNanos);
    }

    public static SchedulingWork beginSchedulingWork(ChunkPos pos, ChunkStatus status) {
        if (!BuildConfig.DEBUG || !enabled) {
            return null;
        }
        SchedulingState state = STAGE_SCHEDULING.get(new StageKey(pos.toLong(), status.getIndex()));
        return state == null ? null : new SchedulingWork(state, System.nanoTime());
    }

    public static void finishSchedulingWork(SchedulingWork work) {
        if (!BuildConfig.DEBUG || work == null || !enabled || EPOCH.get() != work.state.epoch) {
            return;
        }
        work.state.schedulingNanos.addAndGet(System.nanoTime() - work.startedNanos);
    }

    public static long beginStage(ChunkPos pos, ChunkStatus status) {
        if (!BuildConfig.DEBUG || !enabled) {
            return 0L;
        }
        long now = System.nanoTime();
        SchedulingState state = STAGE_SCHEDULING.remove(new StageKey(pos.toLong(), status.getIndex()));
        if (state != null && EPOCH.get() == state.epoch) {
            AtomicLong lastStageFinish = LAST_STAGE_FINISH_NANOS.get(pos.toLong());
            long waitingStartedNanos = lastStageFinish == null ? state.startedNanos : Math.max(state.startedNanos, lastStageFinish.get());
            long totalNanos = now - waitingStartedNanos;
            long schedulingNanos = Math.min(totalNanos, state.schedulingNanos.get());
            ChunkScheduling chunkScheduling = CHUNK_SCHEDULING.computeIfAbsent(pos.toLong(), ignored -> new ChunkScheduling());
            chunkScheduling.schedulingNanos.addAndGet(schedulingNanos);
            chunkScheduling.waitingNanos.addAndGet(totalNanos - schedulingNanos);
        }
        return now;
    }

    public static NoiseProfile beginNoiseProfile() {
        return BuildConfig.DEBUG && enabled ? new NoiseProfile(EPOCH.get()) : null;
    }

    public static void finishNoiseProfile(NoiseProfile profile) {
        if (!BuildConfig.DEBUG || profile == null || !enabled || EPOCH.get() != profile.epoch) {
            return;
        }
        for (int i = 0; i < NOISE_SUBSTAGES.length; i++) {
            updateAverage(NOISE_AVERAGE_NANOS, i, profile.elapsedNanos[i]);
        }
    }

    public static <T> CompletableFuture<T> track(ChunkStatus status, ChunkPos pos, long startedNanos, CompletableFuture<T> future) {
        if (!BuildConfig.DEBUG || startedNanos == 0L) {
            return future;
        }

        int index = status.getIndex();
        long epoch = EPOCH.get();
        future.whenComplete((value, throwable) -> {
            if (enabled && EPOCH.get() == epoch) {
                long finishedNanos = System.nanoTime();
                updateAverage(AVERAGE_NANOS, index, finishedNanos - startedNanos);
                LAST_STAGE_FINISH_NANOS.computeIfAbsent(pos.toLong(), ignored -> new AtomicLong()).accumulateAndGet(finishedNanos, Math::max);
                if (status == ChunkStatus.FULL) {
                    ChunkScheduling chunkScheduling = CHUNK_SCHEDULING.remove(pos.toLong());
                    if (chunkScheduling != null) {
                        updateAverage(SCHEDULING_AVERAGE_NANOS, chunkScheduling.schedulingNanos.get());
                        updateAverage(WAITING_AVERAGE_NANOS, chunkScheduling.waitingNanos.get());
                    }
                    LAST_STAGE_FINISH_NANOS.remove(pos.toLong());
                }
            }
        });
        return future;
    }

    public static List<StageTiming> snapshot() {
        if (!BuildConfig.DEBUG) {
            return List.of();
        }
        List<StageTiming> timings = new ArrayList<>(STATUSES.size() + NOISE_SUBSTAGES.length + 2);
        timings.add(new StageTiming("SCHEDULING", SCHEDULING_AVERAGE_NANOS.get()));
        for (ChunkStatus status : STATUSES) {
            int index = status.getIndex();
            String name = status.toString();
            int separator = name.indexOf(':');
            timings.add(new StageTiming(name.substring(separator + 1).toUpperCase(Locale.ROOT), AVERAGE_NANOS.get(index)));
        }
        Collections.reverse(timings);
        StageTiming scheduling = timings.remove(timings.size() - 1);
        timings.add(0, scheduling);
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
                noiseTimings.add(new StageTiming("  " + substage.displayName, NOISE_AVERAGE_NANOS.get(index)));
            }
            timings.addAll(noiseIndex + 1, noiseTimings);
        }
        timings.add(new StageTiming("WAITING", WAITING_AVERAGE_NANOS.get()));
        return timings;
    }

    private static void updateAverage(AtomicLong average, long sample) {
        long current;
        long updated;
        do {
            current = average.get();
            updated = current == 0L ? sample : current + (sample - current) / 100L;
        } while (!average.compareAndSet(current, updated));
    }

    private static void updateAverage(AtomicLongArray averages, int index, long sample) {
        long current;
        long updated;
        do {
            current = averages.get(index);
            updated = current == 0L ? sample : current + (sample - current) / 100L;
        } while (!averages.compareAndSet(index, current, updated));
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

    private record StageKey(long chunkPos, int statusIndex) {
    }

    private static final class SchedulingState {
        private final long epoch;
        private final long startedNanos;
        private final AtomicLong schedulingNanos = new AtomicLong();

        private SchedulingState(long epoch, long startedNanos) {
            this.epoch = epoch;
            this.startedNanos = startedNanos;
        }
    }

    private static final class ChunkScheduling {
        private final AtomicLong schedulingNanos = new AtomicLong();
        private final AtomicLong waitingNanos = new AtomicLong();
    }

    public static final class SchedulingWork {
        private final SchedulingState state;
        private final long startedNanos;

        private SchedulingWork(SchedulingState state, long startedNanos) {
            this.state = state;
            this.startedNanos = startedNanos;
        }
    }

    public record StageTiming(String name, long averageNanos) {
    }
}
