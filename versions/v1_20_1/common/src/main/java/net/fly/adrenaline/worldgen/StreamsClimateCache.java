package net.fly.adrenaline.worldgen;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
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
            this.cacheable = (cacheable(sampler.temperature()) ? 1 : 0)
                | (cacheable(sampler.humidity()) ? 2 : 0)
                | (cacheable(sampler.continentalness()) ? 4 : 0)
                | (cacheable(sampler.erosion()) ? 8 : 0)
                | (cacheable(sampler.depth()) ? 16 : 0)
                | (cacheable(sampler.weirdness()) ? 32 : 0);
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
            float temperature = this.value(sampler.temperature(), point, base, 1, hit);
            float humidity = this.value(sampler.humidity(), point, base + 1, 2, hit);
            float continentalness = this.value(sampler.continentalness(), point, base + 2, 4, hit);
            float erosion = this.value(sampler.erosion(), point, base + 3, 8, hit);
            float depth = this.value(sampler.depth(), point, base + 4, 16, hit);
            float weirdness = this.value(sampler.weirdness(), point, base + 5, 32, hit);
            this.keys[slot] = key;
            this.valid[slot] = true;
            return Climate.target(temperature, humidity, continentalness, erosion, depth, weirdness);
        } finally {
            this.sampling = false;
        }
    }

    private float value(DensityFunction function, DensityFunction.FunctionContext point, int index, int flag, boolean hit) {
        if ((this.cacheable & flag) == 0) {
            return (float) function.compute(point);
        }
        if (hit) {
            return this.samples[index];
        }
        float value = (float) function.compute(point);
        this.samples[index] = value;
        return value;
    }

    private static boolean cacheable(DensityFunction function) {
        if (SurfacePipelineCompatibility.hasForeignHooks(function.getClass())) {
            return false;
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
