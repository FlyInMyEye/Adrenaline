package net.fly.adrenaline.natives;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.ref.Reference;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinImprovedNoiseAccess;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

public final class PerlinNativeSampler {

    private PerlinNativeSampler() {
    }

    public static boolean sample(Data first, Data second, double valueFactor, double x, double y, double z, double yStep, int count, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.PERLIN);
        try {
            return sample0(first.address, first.octaves, second.address, second.octaves, valueFactor, x, y, z, yStep, count, output);
        } finally {
            Reference.reachabilityFence(first);
            Reference.reachabilityFence(second);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.PERLIN, startedNanos);
        }
    }

    private static native boolean sample0(long firstAddress, int firstOctaves, long secondAddress, int secondOctaves, double valueFactor, double x, double y, double z, double yStep, int count, double[] output);

    public static boolean sampleGrid(Data first, Data second, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.PERLIN);
        try {
            return sampleGrid0(first.address, first.octaves, second.address, second.octaves, valueFactor, x, y, z, xStep, yStep, zStep, xCount, yCount, zCount, output);
        } finally {
            Reference.reachabilityFence(first);
            Reference.reachabilityFence(second);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.PERLIN, startedNanos);
        }
    }

    private static native boolean sampleGrid0(long firstAddress, int firstOctaves, long secondAddress, int secondOctaves, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, double[] output);

    public static boolean sampleApproximateGrid(Data first, Data second, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.PERLIN);
        try {
            return sampleApproximateGrid0(first.address, first.octaves, second.address, second.octaves, valueFactor, x, y, z, xStep, yStep, zStep, xCount, yCount, zCount, output);
        } finally {
            Reference.reachabilityFence(first);
            Reference.reachabilityFence(second);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.PERLIN, startedNanos);
        }
    }

    private static native boolean sampleApproximateGrid0(long firstAddress, int firstOctaves, long secondAddress, int secondOctaves, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, double[] output);

    public static boolean sampleApproximateGridFloat(Data first, Data second, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, float[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.PERLIN);
        try {
            return sampleApproximateGridFloat0(first.address, first.octaves, second.address, second.octaves, valueFactor, x, y, z, xStep, yStep, zStep, xCount, yCount, zCount, output);
        } finally {
            Reference.reachabilityFence(first);
            Reference.reachabilityFence(second);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.PERLIN, startedNanos);
        }
    }

    private static native boolean sampleApproximateGridFloat0(long firstAddress, int firstOctaves, long secondAddress, int secondOctaves, double valueFactor, double x, double y, double z, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount, float[] output);

    private static native long address0(ByteBuffer packed);

    public static Data create(ImprovedNoise[] noiseLevels, DoubleList amplitudes, double lowestFreqValueFactor, double lowestFreqInputFactor) {
        int octaves = noiseLevels.length;
        ByteBuffer packed = ByteBuffer.allocateDirect(16 + octaves * 33 + octaves * 256).order(ByteOrder.nativeOrder());
        packed.putDouble(lowestFreqValueFactor);
        packed.putDouble(lowestFreqInputFactor);
        for (int i = 0; i < octaves; i++) {
            packed.putDouble(amplitudes.getDouble(i));
        }
        for (int i = 0; i < octaves; i++) {
            ImprovedNoise noise = noiseLevels[i];
            if (noise == null) {
                packed.putDouble(0.0D);
                packed.putDouble(0.0D);
                packed.putDouble(0.0D);
            } else {
                AdrenalineMixinImprovedNoiseAccess access = (AdrenalineMixinImprovedNoiseAccess) (Object) noise;
                packed.putDouble(access.adrenaline$getXo());
                packed.putDouble(access.adrenaline$getYo());
                packed.putDouble(access.adrenaline$getZo());
            }
        }
        for (int i = 0; i < octaves; i++) {
            packed.put((byte) (noiseLevels[i] == null ? 0 : 1));
        }
        for (int i = 0; i < octaves; i++) {
            ImprovedNoise noise = noiseLevels[i];
            if (noise == null) {
                for (int index = 0; index < 256; index++) {
                    packed.put((byte) 0);
                }
            } else {
                packed.put(((AdrenalineMixinImprovedNoiseAccess) (Object) noise).adrenaline$getPermutation());
            }
        }
        packed.clear();
        return new Data(packed, octaves, AdrenalineNatives.isAvailable() ? address0(packed) : 0L);
    }

    public static final class Data {
        private final ByteBuffer packed;
        private final int octaves;
        private final long address;

        private Data(ByteBuffer packed, int octaves, long address) {
            this.packed = packed;
            this.octaves = octaves;
            this.address = address;
        }

        public long address() {
            return this.address;
        }

        public int octaves() {
            return this.octaves;
        }

        public boolean available() {
            return this.address != 0L;
        }
    }
}
