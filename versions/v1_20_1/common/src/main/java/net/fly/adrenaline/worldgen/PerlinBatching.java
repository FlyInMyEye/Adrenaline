package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import java.util.function.DoubleSupplier;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class PerlinBatching {
    private static final ThreadLocal<BatchContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<SectionScope> SECTION_SCOPE = ThreadLocal.withInitial(SectionScope::new);
    private static final ThreadLocal<Integer> VANILLA_SAMPLE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private PerlinBatching() {
    }

    public static void enterSection(PerlinSectionCache sections, int xIndex, int zIndex, DensityFunction.ContextProvider provider) {
        SectionScope scope = SECTION_SCOPE.get();
        scope.sections = sections;
        scope.xIndex = xIndex;
        scope.zIndex = zIndex;
        scope.provider = provider;
        scope.active = true;
    }

    public static void exitSection() {
        SectionScope scope = SECTION_SCOPE.get();
        scope.sections = null;
        scope.provider = null;
        scope.active = false;
    }

    public static double tryGetSection(Object key, NormalNoise noise, double xzScale, double yScale, int blockX, int blockY, int blockZ) {
        SectionScope scope = SECTION_SCOPE.get();
        return scope.active ? scope.sections.valueAt(key, noise, xzScale, yScale, blockX, blockY, blockZ, scope.xIndex, scope.zIndex) : Double.NaN;
    }

    public static boolean tryFillSection(Object key, NormalNoise noise, double xzScale, double yScale, double[] values, DensityFunction.ContextProvider provider) {
        SectionScope scope = SECTION_SCOPE.get();
        if (!scope.active) {
            return false;
        }
        if (scope.provider != provider) {
            return false;
        }
        return scope.sections.fillColumn(key, noise, xzScale, yScale, scope.xIndex, scope.zIndex, values);
    }

    public static void begin(int sampleCount) {
        BatchContext context = CONTEXT.get();
        if (context == null) {
            context = new BatchContext();
            CONTEXT.set(context);
        }
        context.begin(sampleCount);
    }

    public static void end() {
        BatchContext context = CONTEXT.get();
        if (context != null) {
            context.end();
        }
    }

    public static double tryGet(NormalNoise noise, double x, double y, double z) {
        BatchContext context = CONTEXT.get();
        return context == null ? Double.NaN : context.tryGet(noise, x, y, z);
    }

    public static void record(NormalNoise noise, double x, double y, double z, double value) {
        BatchContext context = CONTEXT.get();
        if (context != null) {
            context.record(noise, x, y, z, value);
        }
    }

    public static boolean isVanillaSampleSuppressed() {
        return VANILLA_SAMPLE_DEPTH.get() != 0;
    }

    public static double sampleVanilla(DoubleSupplier sampler) {
        int depth = VANILLA_SAMPLE_DEPTH.get();
        VANILLA_SAMPLE_DEPTH.set(depth + 1);
        try {
            return sampler.getAsDouble();
        } finally {
            VANILLA_SAMPLE_DEPTH.set(depth);
        }
    }

    private static final class SectionScope {
        private PerlinSectionCache sections;
        private int xIndex;
        private int zIndex;
        private DensityFunction.ContextProvider provider;
        private boolean active;
    }

    private static final class BatchContext {
        private final IdentityHashMap<NormalNoise, State> states = new IdentityHashMap<>();
        private State[] pool = new State[4];
        private int poolSize;
        private int depth;
        private int sampleCount;

        private void begin(int sampleCount) {
            if (this.depth != 0) {
                this.reset();
            }
            this.depth = 1;
            this.sampleCount = Math.min(sampleCount, 256);
            this.poolSize = 0;
            this.states.clear();
        }

        private void end() {
            if (this.depth != 0) {
                this.reset();
            }
        }

        private void reset() {
            for (int i = 0; i < this.poolSize; i++) {
                this.pool[i].reset();
            }
            this.states.clear();
            this.poolSize = 0;
            this.sampleCount = 0;
            this.depth = 0;
        }

        private double tryGet(NormalNoise noise, double x, double y, double z) {
            if (this.depth == 0) {
                return Double.NaN;
            }
            State state = this.states.get(noise);
            return state == null ? Double.NaN : state.tryGet(x, y, z);
        }

        private void record(NormalNoise noise, double x, double y, double z, double value) {
            if (this.depth == 0 || this.sampleCount < 3) {
                return;
            }
            State state = this.states.get(noise);
            if (state == null) {
                state = this.nextState();
                this.states.put(noise, state);
            }
            state.record(noise, x, y, z, value, this.sampleCount);
        }

        private State nextState() {
            if (this.poolSize == this.pool.length) {
                State[] expanded = new State[this.pool.length * 2];
                System.arraycopy(this.pool, 0, expanded, 0, this.pool.length);
                this.pool = expanded;
            }
            State state = this.pool[this.poolSize];
            if (state == null) {
                state = new State();
                this.pool[this.poolSize] = state;
            }
            this.poolSize++;
            return state;
        }
    }

    private static final class State {
        private boolean hasFirst;
        private boolean ready;
        private boolean disabled;
        private double firstX;
        private double firstY;
        private double firstZ;
        private double batchY;
        private double yStep;
        private int nextIndex;
        private int count;
        private double[] values = new double[0];

        private double tryGet(double x, double y, double z) {
            if (!this.ready || !same(x, this.firstX) || !same(z, this.firstZ) || !same(y, this.batchY + this.nextIndex * this.yStep)) {
                if (this.ready) {
                    this.ready = false;
                    this.disabled = true;
                }
                return Double.NaN;
            }
            double value = this.values[this.nextIndex++];
            if (this.nextIndex == this.count) {
                this.ready = false;
                this.disabled = true;
            }
            return value;
        }

        private void record(NormalNoise noise, double x, double y, double z, double value, int sampleCount) {
            if (this.disabled || this.ready) {
                return;
            }
            if (!this.hasFirst) {
                this.hasFirst = true;
                this.firstX = x;
                this.firstY = y;
                this.firstZ = z;
                return;
            }
            if (!same(x, this.firstX) || !same(z, this.firstZ)) {
                this.disabled = true;
                return;
            }
            this.count = sampleCount - 1;
            if (this.values.length < this.count) {
                this.values = new double[this.count];
            }
            this.batchY = y;
            this.yStep = y - this.firstY;
            AdrenalineNormalNoiseNativeAccess access = (AdrenalineNormalNoiseNativeAccess) noise;
            if (!PerlinNativeSampler.sample(access.adrenaline$getFirstNativeData(), access.adrenaline$getSecondNativeData(), access.adrenaline$getNativeValueFactor(), x, y, z, this.yStep, this.count, this.values)) {
                this.disabled = true;
                return;
            }
            if (!same(value, this.values[0])) {
                this.disabled = true;
                return;
            }
            this.ready = true;
            this.nextIndex = 1;
        }

        private void reset() {
            this.hasFirst = false;
            this.ready = false;
            this.disabled = false;
            this.nextIndex = 0;
            this.count = 0;
        }
    }

    private static boolean same(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second)
            || Math.abs(first - second) <= Math.max(1.0E-12D, Math.ulp(second) * 16.0D);
    }

}
