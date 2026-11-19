package net.fly.adrenaline.worldgen;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.IdentityHashMap;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.fly.adrenaline.natives.NativeClimateSampler;
import net.minecraft.core.QuartPos;
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
        }
        if (this.cacheable == 0) {
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
            float depth = this.value(sampler.depth(), point, base + 4, 16, ready);
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
        if (function instanceof AdrenalineConstantFunctionAccess) {
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
        if (function instanceof AdrenalineNoiseFunctionAccess noise) {
            return Double.doubleToRawLongBits(noise.adrenaline$getYScale()) == 0L && unmodifiedNoise();
        }
        return function instanceof AdrenalineShiftedNoiseAccess noise
            && Double.doubleToRawLongBits(noise.adrenaline$getYScale()) == 0L
            && noise.adrenaline$getShiftY() instanceof AdrenalineConstantFunctionAccess constant
            && Double.doubleToRawLongBits(constant.adrenaline$getConstantValue()) == 0L
            && !SurfacePipelineCompatibility.hasForeignHooks(constant.getClass())
            && isShift(noise.adrenaline$getShiftX(), 1) && isShift(noise.adrenaline$getShiftZ(), 2)
            && unmodifiedNoise();
    }

    private static boolean isShift(DensityFunction function, int kind) {
        if (function.getClass() == MARKER) {
            if (SurfacePipelineCompatibility.hasForeignHooks(MARKER)) {
                return false;
            }
            function = ((DensityFunctions.MarkerOrMarked) function).wrapped();
        }
        return function instanceof AdrenalineShiftNoiseAccess noise && noise.adrenaline$getShiftKind() == kind
            && !SurfacePipelineCompatibility.hasForeignHooks(function.getClass());
    }

    private static boolean unmodifiedNoise() {
        return !SurfacePipelineCompatibility.hasForeignHooks(DensityFunction.NoiseHolder.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(NormalNoise.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(PerlinNoise.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(ImprovedNoise.class);
    }
}
