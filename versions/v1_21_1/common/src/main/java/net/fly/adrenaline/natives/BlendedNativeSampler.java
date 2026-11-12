package net.fly.adrenaline.natives;

import java.lang.ref.Reference;

public final class BlendedNativeSampler {

    private BlendedNativeSampler() {
    }

    public static boolean sampleGrid(Data data, int x, int y, int z, int xStep, int yStep, int zStep,
                                     int xCount, int yCount, int zCount, double[] output) {
        if (!AdrenalineNatives.isAvailable() || !data.available()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.PERLIN);
        try {
            return sampleGrid0(data.min.address(), data.max.address(), data.main.address(), data.xzMultiplier,
                data.yMultiplier, data.xzFactor, data.yFactor, data.smearScaleMultiplier,
                x, y, z, xStep, yStep, zStep, xCount, yCount, zCount, output);
        } finally {
            Reference.reachabilityFence(data);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.PERLIN, startedNanos);
        }
    }

    private static native boolean sampleGrid0(long minAddress, long maxAddress, long mainAddress,
                                              double xzMultiplier, double yMultiplier, double xzFactor,
                                              double yFactor, double smearScaleMultiplier,
                                              int x, int y, int z, int xStep, int yStep, int zStep,
                                              int xCount, int yCount, int zCount, double[] output);

    public record Data(PerlinNativeSampler.Data min, PerlinNativeSampler.Data max, PerlinNativeSampler.Data main,
                       double xzMultiplier, double yMultiplier, double xzFactor, double yFactor, double smearScaleMultiplier) {
        public boolean available() {
            return this.min.available() && this.max.available() && this.main.available()
                && this.min.octaves() == 16 && this.max.octaves() == 16 && this.main.octaves() == 8
                && this.xzMultiplier >= 0.684412 && this.xzMultiplier <= 684412.0
                && this.yMultiplier >= 0.684412 && this.yMultiplier <= 684412.0
                && this.xzFactor >= 0.001 && this.xzFactor <= 1000.0
                && this.yFactor >= 0.001 && this.yFactor <= 1000.0
                && this.smearScaleMultiplier >= 1.0 && this.smearScaleMultiplier <= 8.0;
        }
    }
}
