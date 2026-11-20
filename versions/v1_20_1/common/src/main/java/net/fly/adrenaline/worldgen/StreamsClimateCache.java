package net.fly.adrenaline.worldgen;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.IdentityHashMap;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.fly.adrenaline.natives.NativeClimateSampler;
import net.minecraft.core.QuartPos;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.jetbrains.annotations.Nullable;

public final class StreamsClimateCache {

    private static final int SIZE = 256;
    private static final ThreadLocal<StreamsClimateCache> CACHES = new ThreadLocal<>();
    private static final Class<?> MARKER = DensityFunctions.flatCache(DensityFunctions.zero()).getClass();

    private WeakReference<Climate.Sampler> sampler = new WeakReference<>(null);
    private final long[] keys = new long[SIZE];
    private final boolean[] valid = new boolean[SIZE];
    private final float[] samples = new float[SIZE * 6];
    private int cacheable;
    private DepthSampler depthSampler;
    private boolean active;
    private boolean sampling;
    private boolean nativePlanInitialized;
    private NativeClimateSampler nativePlan;

    public static StreamsClimateCache forQuery() {
        StreamsClimateCache cache = CACHES.get();
        if (cache == null) {
            cache = new StreamsClimateCache();
            CACHES.set(cache);
        }
        return cache;
    }

    public static @Nullable Climate.TargetPoint sampleActive(Climate.Sampler sampler, int x, int y, int z) {
        StreamsClimateCache cache = CACHES.get();
        return cache == null ? null : cache.sample(sampler, x, y, z);
    }

    public boolean setActive(boolean active) {
        boolean previous = this.active;
        this.active = active;
        return previous;
    }

    private @Nullable Climate.TargetPoint sample(Climate.Sampler sampler, int x, int y, int z) {
        if (!this.active || this.sampling) {
            return null;
        }
        if (this.sampler.get() != sampler) {
            this.sampler = new WeakReference<>(sampler);
            Arrays.fill(this.valid, false);
            this.nativePlanInitialized = false;
            this.nativePlan = null;
            var known = new IdentityHashMap<DensityFunction, Boolean>();
            this.cacheable = (cacheable(sampler.temperature(), known, 0) ? 1 : 0)
                | (cacheable(sampler.humidity(), known, 0) ? 2 : 0)
                | (cacheable(sampler.continentalness(), known, 0) ? 4 : 0)
                | (cacheable(sampler.erosion(), known, 0) ? 8 : 0)
                | (cacheable(sampler.depth(), known, 0) ? 16 : 0)
                | (cacheable(sampler.weirdness(), known, 0) ? 32 : 0);
            this.depthSampler = (this.cacheable & 16) == 0 ? depthSampler(sampler.depth(), known, new IdentityHashMap<>(), true, 0) : null;
        }
        if (this.cacheable == 0 && this.depthSampler == null) {
            return null;
        }

        long key = ((long) x << 32) | (z & 0xffffffffL);
        int slot = (x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b, 16)) & (SIZE - 1);
        boolean hit = this.valid[slot] && this.keys[slot] == key;
        this.valid[slot] = false;
        int base = slot * 6;
        var point = new DensityFunction.SinglePointContext(QuartPos.toBlock(x), QuartPos.toBlock(y), QuartPos.toBlock(z));
        this.sampling = true;
        try {
            int ready = hit ? this.cacheable : this.sampleNative(sampler, point.blockX(), point.blockZ(), base);
            float temperature = this.value(sampler.temperature(), point, base, 1, ready);
            float humidity = this.value(sampler.humidity(), point, base + 1, 2, ready);
            float continentalness = this.value(sampler.continentalness(), point, base + 2, 4, ready);
            float erosion = this.value(sampler.erosion(), point, base + 3, 8, ready);
            float depth = this.depthSampler == null ? this.value(sampler.depth(), point, base + 4, 16, ready)
                : (float) this.depthSampler.sample(point, slot);
            float weirdness = this.value(sampler.weirdness(), point, base + 5, 32, ready);
            this.keys[slot] = key;
            this.valid[slot] = true;
            return Climate.target(temperature, humidity, continentalness, erosion, depth, weirdness);
        } finally {
            this.sampling = false;
        }
    }

    private int sampleNative(Climate.Sampler sampler, int x, int z, int base) {
        if (!AdrenalineConfig.nativePerlinBatchingEnabled()
            || !AdrenalineNatives.isAvailable(AdrenalineNatives.Feature.CLIMATE)) {
            return 0;
        }
        if (!this.nativePlanInitialized) {
            this.nativePlan = NativeClimateSampler.create(sampler, this.cacheable);
            this.nativePlanInitialized = true;
        }
        return this.nativePlan == null ? 0 : this.nativePlan.sample(x, z, this.samples, base);
    }

    private float value(DensityFunction function, DensityFunction.FunctionContext point, int index, int flag, int ready) {
        if ((this.cacheable & flag) == 0) {
            return (float) function.compute(point);
        }
        if ((ready & flag) != 0) {
            return this.samples[index];
        }
        float value = (float) function.compute(point);
        this.samples[index] = value;
        return value;
    }

    private static @Nullable DepthSampler depthSampler(
        DensityFunction function, IdentityHashMap<DensityFunction, Boolean> known,
        IdentityHashMap<DensityFunction, DepthSampler> offsets, boolean allowRange, int depth
    ) {
        if (depth > 24 || SurfacePipelineCompatibility.hasForeignHooks(function.getClass())) {
            return null;
        }
        if (function.getClass() == MARKER) {
            return depthSampler(((DensityFunctions.MarkerOrMarked) function).wrapped(), known, offsets, allowRange, depth + 1);
        }
        if (function instanceof DensityFunctions.HolderHolder holder) {
            return depthSampler(holder.function().value(), known, offsets, allowRange, depth + 1);
        }
        if (function instanceof AdrenalineClampFunctionAccess clamp) {
            DepthSampler input = depthSampler(clamp.adrenaline$clampInput(), known, offsets, allowRange, depth + 1);
            double min = clamp.adrenaline$clampMin();
            double max = clamp.adrenaline$clampMax();
            return input == null ? null : (point, slot) -> Mth.clamp(input.sample(point, slot), min, max);
        }
        if (allowRange && function instanceof AdrenalineRangeChoiceAccess range
            && range.adrenaline$rangeInput() instanceof AdrenalineYGradientAccess
            && !SurfacePipelineCompatibility.hasForeignHooks(range.adrenaline$rangeInput().getClass())) {
            DepthSampler in = depthSampler(range.adrenaline$whenInRange(), known, offsets, false, depth + 1);
            DepthSampler out = depthSampler(range.adrenaline$whenOutOfRange(), known, offsets, false, depth + 1);
            if (in == null || out == null) {
                return null;
            }
            var input = range.adrenaline$rangeInput();
            double min = range.adrenaline$minInclusive();
            double max = range.adrenaline$maxExclusive();
            return (point, slot) -> {
                double value = input.compute(point);
                return (value >= min && value < max ? in : out).sample(point, slot);
            };
        }
        if (function instanceof AdrenalineMulOrAddAccess transform) {
            try {
                if (DensityFunctionOperation.ordinal(function) == 1) {
                    DepthSampler input = depthSampler(transform.adrenaline$transformInput(), known, offsets, allowRange, depth + 1);
                    double argument = transform.adrenaline$transformArgument();
                    return input == null ? null : (point, slot) -> input.sample(point, slot) + argument;
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                return null;
            }
        }
        if (function instanceof AdrenalineBinaryFunctionAccess binary
            && binary.adrenaline$firstArgument() instanceof AdrenalineYGradientAccess
            && !SurfacePipelineCompatibility.hasForeignHooks(binary.adrenaline$firstArgument().getClass())
            && cacheable(binary.adrenaline$secondArgument(), known, depth + 1)) {
            try {
                if (DensityFunctionOperation.ordinal(function) == 0) {
                    var gradient = binary.adrenaline$firstArgument();
                    DepthSampler offset = offsets.computeIfAbsent(binary.adrenaline$secondArgument(), StreamsClimateCache::columnSampler);
                    return (point, slot) -> gradient.compute(point) + offset.sample(point, slot);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static DepthSampler columnSampler(DensityFunction function) {
        var values = new double[SIZE];
        var keys = new long[SIZE];
        var valid = new boolean[SIZE];
        return (point, slot) -> {
            long key = ((long) point.blockX() << 32) | (point.blockZ() & 0xffffffffL);
            if (!valid[slot] || keys[slot] != key) {
                values[slot] = function.compute(point);
                keys[slot] = key;
                valid[slot] = true;
            }
            return values[slot];
        };
    }

    @FunctionalInterface
    private interface DepthSampler {
        double sample(DensityFunction.FunctionContext point, int slot);
    }

    private static boolean cacheable(DensityFunction function, IdentityHashMap<DensityFunction, Boolean> known, int depth) {
        if (depth > 48 || known.size() > 512) {
            return false;
        }
        Boolean previous = known.putIfAbsent(function, false);
        if (previous != null) {
            return previous;
        }
        boolean result = !SurfacePipelineCompatibility.hasForeignHooks(function.getClass()) && inspect(function, known, depth + 1);
        known.put(function, result);
        return result;
    }

    private static boolean inspect(DensityFunction function, IdentityHashMap<DensityFunction, Boolean> known, int depth) {
        if (function instanceof AdrenalineConstantFunctionAccess
            || function == DensityFunctions.blendAlpha() || function == DensityFunctions.blendOffset()) {
            return true;
        }
        if (function instanceof AdrenalineMulOrAddAccess transform) {
            return cacheable(transform.adrenaline$transformInput(), known, depth);
        }
        if (function instanceof AdrenalineBinaryFunctionAccess binary) {
            return cacheable(binary.adrenaline$firstArgument(), known, depth)
                && cacheable(binary.adrenaline$secondArgument(), known, depth);
        }
        if (function instanceof AdrenalineMappedFunctionAccess mapped) {
            return cacheable(mapped.adrenaline$mappedInput(), known, depth);
        }
        if (function instanceof AdrenalineClampFunctionAccess clamp) {
            return cacheable(clamp.adrenaline$clampInput(), known, depth);
        }
        if (function.getClass() == MARKER) {
            return cacheable(((DensityFunctions.MarkerOrMarked) function).wrapped(), known, depth);
        }
        if (function instanceof DensityFunctions.HolderHolder holder) {
            return cacheable(holder.function().value(), known, depth);
        }
        if (function instanceof AdrenalineRangeChoiceAccess range) {
            return cacheable(range.adrenaline$rangeInput(), known, depth)
                && cacheable(range.adrenaline$whenInRange(), known, depth)
                && cacheable(range.adrenaline$whenOutOfRange(), known, depth);
        }
        if (function instanceof DensityFunctions.Spline spline) {
            return cacheableSpline(spline.spline(), known, depth);
        }
        if (function instanceof AdrenalineNoiseFunctionAccess noise) {
            return Double.doubleToRawLongBits(noise.adrenaline$getYScale()) == 0L && unmodifiedNoise();
        }
        return function instanceof AdrenalineShiftedNoiseAccess noise
            && Double.doubleToRawLongBits(noise.adrenaline$getYScale()) == 0L
            && noise.adrenaline$getShiftY() instanceof AdrenalineConstantFunctionAccess constant
            && Double.doubleToRawLongBits(constant.adrenaline$getConstantValue()) == 0L
            && !SurfacePipelineCompatibility.hasForeignHooks(constant.getClass())
            && isColumnShift(noise.adrenaline$getShiftX(), 1) && isColumnShift(noise.adrenaline$getShiftZ(), 2)
            && unmodifiedNoise();
    }

    private static boolean cacheableSpline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
        IdentityHashMap<DensityFunction, Boolean> known, int depth) {
        if (depth > 48 || SurfacePipelineCompatibility.hasForeignHooks(spline.getClass())) {
            return false;
        }
        if (spline instanceof CubicSpline.Constant<?, ?>) {
            return true;
        }
        if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multipoint)
            || SurfacePipelineCompatibility.hasForeignHooks(multipoint.coordinate().getClass())
            || !cacheable(multipoint.coordinate().function().value(), known, depth + 1)) {
            return false;
        }
        for (var value : multipoint.values()) {
            if (!cacheableSpline(value, known, depth + 1)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isColumnShift(DensityFunction function, int kind) {
        if (function.getClass() == MARKER) {
            if (SurfacePipelineCompatibility.hasForeignHooks(MARKER)) {
                return false;
            }
            function = ((DensityFunctions.MarkerOrMarked) function).wrapped();
        }
        return !SurfacePipelineCompatibility.hasForeignHooks(function.getClass())
            && (function instanceof AdrenalineConstantFunctionAccess
                || function instanceof AdrenalineShiftNoiseAccess noise && noise.adrenaline$getShiftKind() == kind);
    }

    private static boolean unmodifiedNoise() {
        return !SurfacePipelineCompatibility.hasForeignHooks(DensityFunction.NoiseHolder.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(NormalNoise.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(PerlinNoise.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(ImprovedNoise.class);
    }
}
