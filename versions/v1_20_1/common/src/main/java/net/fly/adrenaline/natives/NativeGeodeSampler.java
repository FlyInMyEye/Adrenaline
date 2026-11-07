package net.fly.adrenaline.natives;

import java.lang.ref.Reference;
import net.fly.adrenaline.worldgen.AdrenalineNormalNoiseNativeAccess;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NativeGeodeSampler {

    private NativeGeodeSampler() {
    }

    public static int filter(NormalNoise noise, int x, int y, int z, int size, double multiplier, double threshold, int[] points, int[] output) {
        if (!AdrenalineNatives.isAvailable() || !(noise instanceof AdrenalineNormalNoiseNativeAccess access)) {
            return -1;
        }
        PerlinNativeSampler.Data first = access.adrenaline$getFirstNativeData();
        PerlinNativeSampler.Data second = access.adrenaline$getSecondNativeData();
        if (!first.available() || !second.available()) {
            return -1;
        }
        try {
            return filter0(first.address(), first.octaves(), second.address(), second.octaves(), access.adrenaline$getNativeValueFactor(),
                x, y, z, size, multiplier, threshold, points, output);
        } finally {
            Reference.reachabilityFence(first);
            Reference.reachabilityFence(second);
        }
    }

    private static native int filter0(long first, int firstCount, long second, int secondCount, double factor,
                                      int x, int y, int z, int size, double multiplier, double threshold, int[] points, int[] output);
}
