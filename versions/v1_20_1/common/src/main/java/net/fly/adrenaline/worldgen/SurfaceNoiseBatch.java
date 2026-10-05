package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SurfaceNoiseBatch implements AutoCloseable {

    private static final ThreadLocal<SurfaceNoiseBatch> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<SamplePool> SAMPLE_POOL = ThreadLocal.withInitial(SamplePool::new);
    private final SurfaceNoiseBatch previous;
    private final SamplePool samples;
    private final NormalNoise noise;
    private final int x;
    private final int z;
    private double[] values;
    private boolean attempted;

    public SurfaceNoiseBatch(NormalNoise noise, int x, int z) {
        this.previous = CURRENT.get();
        this.samples = !AdrenalineConfig.surfaceNoiseOptimizationsEnabled() ? null
            : this.previous == null ? SAMPLE_POOL.get() : new SamplePool();
        this.noise = noise;
        this.x = x;
        this.z = z;
        CURRENT.set(this);
    }

    public static double sampleExact(NormalNoise noise, double x, double y, double z) {
        if (noise.getClass() != NormalNoise.class || PerlinBatching.isVanillaSampleSuppressed()) {
            return noise.getValue(x, y, z);
        }
        double value = tryGet(noise, x, y, z);
        if (Double.isNaN(value)) {
            value = noise.getValue(x, y, z);
            record(noise, x, y, z, value);
        }
        return value;
    }

    public static double tryGet(NormalNoise noise, double x, double y, double z) {
        SurfaceNoiseBatch batch = CURRENT.get();
        if (batch == null || batch.samples == null || PerlinBatching.isActive()) {
            return Double.NaN;
        }
        Samples samples = batch.samples.byNoise.get(noise);
        return samples == null ? Double.NaN : samples.get(x, y, z);
    }

    public static void record(NormalNoise noise, double x, double y, double z, double value) {
        SurfaceNoiseBatch batch = CURRENT.get();
        if (batch != null && batch.samples != null && !PerlinBatching.isActive()) {
            Samples samples = batch.samples.get(noise);
            if (samples != null) {
                samples.put(x, y, z, value);
            }
        }
    }

    public static double sample(NormalNoise noise, int x, int z) {
        SurfaceNoiseBatch batch = CURRENT.get();
        if (batch == null || batch.noise != noise || x < batch.x || x >= batch.x + 16 || z < batch.z || z >= batch.z + 16
            || !AdrenalineConfig.nativePerlinBatchingEnabled()) {
            return noise.getValue(x, 0.0, z);
        }
        if (!batch.attempted) {
            batch.attempted = true;
            AdrenalineNormalNoiseNativeAccess access = (AdrenalineNormalNoiseNativeAccess) noise;
            double[] tile = new double[256];
            if (PerlinNativeSampler.sampleGrid(access.adrenaline$getFirstNativeData(), access.adrenaline$getSecondNativeData(),
                access.adrenaline$getNativeValueFactor(), batch.x, 0.0, batch.z, 1.0, 0.0, 1.0, 16, 1, 16, tile)) {
                batch.values = tile;
            }
        }
        return batch.values == null ? noise.getValue(x, 0.0, z) : batch.values[(x - batch.x) * 16 + z - batch.z];
    }

    @Override
    public void close() {
        if (this.samples != null) {
            this.samples.byNoise.clear();
            this.samples.used = 0;
        }
        if (this.previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(this.previous);
        }
    }

    private static final class SamplePool {
        private static final int LIMIT = 32;
        private final IdentityHashMap<NormalNoise, Samples> byNoise = new IdentityHashMap<>();
        private final Samples[] pool = new Samples[LIMIT];
        private int used;

        private Samples get(NormalNoise noise) {
            Samples samples = this.byNoise.get(noise);
            if (samples == null) {
                if (this.used == this.pool.length) {
                    return null;
                }
                samples = this.pool[this.used];
                if (samples == null) {
                    samples = new Samples();
                    this.pool[this.used] = samples;
                }
                this.used++;
                samples.reset();
                this.byNoise.put(noise, samples);
            }
            return samples;
        }
    }

    private static final class Samples {
        private long x;
        private long y;
        private long z;
        private double value;
        private boolean present;

        private void reset() {
            this.present = false;
        }

        private double get(double x, double y, double z) {
            return this.present && this.x == Double.doubleToRawLongBits(x)
                && this.y == Double.doubleToRawLongBits(y) && this.z == Double.doubleToRawLongBits(z)
                ? this.value : Double.NaN;
        }

        private void put(double x, double y, double z, double value) {
            this.x = Double.doubleToRawLongBits(x);
            this.y = Double.doubleToRawLongBits(y);
            this.z = Double.doubleToRawLongBits(z);
            this.value = value;
            this.present = true;
        }
    }
}
