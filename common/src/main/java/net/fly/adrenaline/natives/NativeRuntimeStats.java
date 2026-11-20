package net.fly.adrenaline.natives;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class NativeRuntimeStats {

    private static final int SAMPLE_MASK = 255;
    private static final boolean DEBUG = NativeRuntimeStats.debugBuild();
    private static final StageStats[] STAGES = {
        new StageStats("Perlin"),
        new StageStats("Density"),
        new StageStats("Aquifer prepare"),
        new StageStats("Aquifer final"),
        new StageStats("Climate"),
        new StageStats("Ore candidates"),
        new StageStats("Surface"),
        new StageStats("Geode"),
        new StageStats("Air bounds"),
        new StageStats("Aquifer locate"),
        new StageStats("Aquifer classify"),
        new StageStats("Aquifer context")
    };
    private static final ThreadLocal<WorkerSamples> WORKER_SAMPLES = ThreadLocal.withInitial(WorkerSamples::new);

    private NativeRuntimeStats() {
    }

    public static long begin(Stage stage) {
        if (!DEBUG) {
            return 0L;
        }
        int count = ++WORKER_SAMPLES.get().counters[stage.ordinal()];
        if (((count ^ (count >>> 8)) & SAMPLE_MASK) != 0) {
            return 0L;
        }
        return System.nanoTime();
    }

    public static void end(Stage stage, long startedNanos) {
        if (startedNanos == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        WorkerSamples worker = WORKER_SAMPLES.get();
        if (worker.timer != null) {
            worker.nanos += elapsed;
        }
        StageStats stats = STAGES[stage.ordinal()];
        stats.samples.increment();
        stats.estimatedCalls.add(SAMPLE_MASK + 1L);
        stats.nanos.add(elapsed);
    }

    public static <T> Supplier<T> wrapStageTask(Supplier<T> task) {
        if (!DEBUG) {
            return task;
        }
        StageTimer timer = WORKER_SAMPLES.get().timer;
        return timer == null ? task : () -> timer.run(task);
    }

    public static String summary() {
        if (!DEBUG) {
            return "Native runtime stats are only available in debug builds.";
        }
        StringBuilder result = new StringBuilder("Native stages (1/256 sampled): ");
        boolean hasSamples = false;
        for (StageStats stage : STAGES) {
            long samples = stage.samples.sum();
            if (samples == 0L) {
                continue;
            }
            if (hasSamples) {
                result.append("; ");
            }
            double averageMicros = stage.nanos.sum() / (double) samples / 1_000.0D;
            result.append(stage.name).append(' ')
                .append(stage.estimatedCalls.sum()).append(" calls, avg ")
                .append(String.format(Locale.ROOT, "%.2f", averageMicros)).append(" us");
            hasSamples = true;
        }
        return hasSamples ? result.toString() : "Native stages have not run yet.";
    }

    public enum Stage {
        PERLIN,
        DENSITY,
        AQUIFER_PREPARE,
        AQUIFER_FINAL,
        CLIMATE,
        ORE_CANDIDATES,
        SURFACE,
        GEODE,
        AIR_BOUNDS,
        AQUIFER_LOCATE,
        AQUIFER_CLASSIFY,
        AQUIFER_CONTEXT
    }

    private static boolean debugBuild() {
        try {
            return Class.forName("net.fly.adrenaline.BuildConfig").getField("DEBUG").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static final class StageTimer {
        private final AtomicLong sampledNanos = new AtomicLong();

        public <T> T run(Supplier<T> task) {
            WorkerSamples worker = WORKER_SAMPLES.get();
            StageTimer previous = worker.timer;
            if (previous == this) {
                return task.get();
            }
            long previousNanos = worker.nanos;
            worker.timer = this;
            worker.nanos = 0L;
            try {
                return task.get();
            } finally {
                long nanos = worker.nanos;
                worker.nanos = previousNanos;
                worker.timer = previous;
                if (nanos != 0L) {
                    sampledNanos.addAndGet(nanos);
                }
            }
        }

        public long estimatedNanos() {
            return sampledNanos.get() * (SAMPLE_MASK + 1L);
        }
    }

    private static final class WorkerSamples {
        private final int[] counters = new int[STAGES.length];
        private StageTimer timer;
        private long nanos;
    }

    private static final class StageStats {
        private final String name;
        private final LongAdder samples = new LongAdder();
        private final LongAdder estimatedCalls = new LongAdder();
        private final LongAdder nanos = new LongAdder();

        private StageStats(String name) {
            this.name = name;
        }
    }
}
