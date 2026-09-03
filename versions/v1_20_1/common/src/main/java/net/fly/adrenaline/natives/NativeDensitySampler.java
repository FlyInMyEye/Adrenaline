package net.fly.adrenaline.natives;

import java.lang.ref.Reference;
import net.fly.adrenaline.worldgen.NativeDensityProgram;

public final class NativeDensitySampler {

    private NativeDensitySampler() {
    }

    public static boolean evaluate(NativeDensityProgram program, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.DENSITY);
        try {
            return evaluate0(program.bytecode(), program.length(), baseX, baseY, baseZ, cellWidth, cellHeight, output);
        } finally {
            Reference.reachabilityFence(program);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.DENSITY, startedNanos);
        }
    }

    private static native boolean evaluate0(java.nio.ByteBuffer program, int length, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, double[] output);
}
