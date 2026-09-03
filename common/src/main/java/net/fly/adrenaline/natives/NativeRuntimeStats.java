package net.fly.adrenaline.natives;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

public final class NativeRuntimeStats {

    private static final int SAMPLE_MASK = 255;
    private static final boolean DEBUG = NativeRuntimeStats.debugBuild();
    private static final StageStats[] STAGES = {
        new StageStats("Perlin"),
        new StageStats("Density"),
        new StageStats("Aquifer prepare"),
        new StageStats("Aquifer final")
    };
    private static final ThreadLocal<int[]> COUNTERS = ThreadLocal.withInitial(() -> new int[STAGES.length]);

    private NativeRuntimeStats() {
    }

    public static long begin(Stage stage) {
        if (!DEBUG) {
            return 0L;
        }
        int[] counters = COUNTERS.get();
        int index = stage.ordinal();
        if ((++counters[index] & SAMPLE_MASK) != 0) {
            return 0L;
        }
        return System.nanoTime();
    }

    public static void end(Stage stage, long startedNanos) {
        if (startedNanos == 0L) {
            return;
        }
        StageStats stats = STAGES[stage.ordinal()];
        stats.samples.increment();
        stats.estimatedCalls.add(SAMPLE_MASK + 1L);
        stats.nanos.add(System.nanoTime() - startedNanos);
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
        AQUIFER_FINAL
    }

    private static boolean debugBuild() {
        try {
            return Class.forName("net.fly.adrenaline.BuildConfig").getField("DEBUG").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
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
