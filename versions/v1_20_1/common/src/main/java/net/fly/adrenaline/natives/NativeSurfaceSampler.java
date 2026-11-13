package net.fly.adrenaline.natives;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import net.fly.adrenaline.natives.AdrenalineNatives.Feature;
import net.fly.adrenaline.worldgen.NativeSurfaceProgram;

public final class NativeSurfaceSampler {

    private NativeSurfaceSampler() {
    }

    public static boolean available() {
        return AdrenalineNatives.isAvailable(Feature.SURFACE);
    }

    public static int column(NativeSurfaceProgram program, ByteBuffer input, long inputAddress, ByteBuffer output, long outputAddress, ByteBuffer cache, long cacheAddress, int count, int x, int z) {
        if (!available() || program.address() == 0L || inputAddress == 0L || outputAddress == 0L || cacheAddress == 0L) {
            return -1;
        }
        try {
            return columnAddress5(program.address(), program.length(), inputAddress, outputAddress, cacheAddress, count, x, z);
        } catch (LinkageError | RuntimeException exception) {
            AdrenalineNatives.disable(Feature.SURFACE, exception);
            return -1;
        } finally {
            Reference.reachabilityFence(input);
            Reference.reachabilityFence(output);
            Reference.reachabilityFence(cache);
            Reference.reachabilityFence(program);
        }
    }

    public static long address(ByteBuffer buffer) {
        if (!available()) {
            return 0L;
        }
        try {
            return address0(buffer);
        } catch (LinkageError | RuntimeException exception) {
            AdrenalineNatives.disable(Feature.SURFACE, exception);
            return 0L;
        }
    }

    private static native long address0(ByteBuffer buffer);
    private static native int columnAddress5(long programAddress, int programLength, long inputAddress, long outputAddress, long cacheAddress, int count, int x, int z);
}
