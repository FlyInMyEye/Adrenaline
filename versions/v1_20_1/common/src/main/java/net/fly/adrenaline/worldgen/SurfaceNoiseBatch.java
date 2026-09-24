package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SurfaceNoiseBatch implements AutoCloseable {

    private static final ThreadLocal<SurfaceNoiseBatch> CURRENT = new ThreadLocal<>();
    private final SurfaceNoiseBatch previous;
    private final NormalNoise noise;
    private final int x;
    private final int z;
    private double[] values;
    private boolean attempted;

    public SurfaceNoiseBatch(NormalNoise noise, int x, int z) {
        this.previous = CURRENT.get();
        this.noise = noise;
        this.x = x;
        this.z = z;
        CURRENT.set(this);
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
        if (this.previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(this.previous);
        }
    }
}
