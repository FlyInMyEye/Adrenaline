package net.fly.adrenaline.natives;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
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
            if (program.inputCount() > 0) {
                return program.inputValues() != null && evaluateWithInputs0(program.bytecode(), program.length(), program.inputValues(), program.inputCount(), baseX, baseY, baseZ, cellWidth, cellHeight, output);
            }
            return evaluate0(program.bytecode(), program.length(), baseX, baseY, baseZ, cellWidth, cellHeight, output);
        } finally {
            Reference.reachabilityFence(program);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.DENSITY, startedNanos);
        }
    }

    public static int preliminarySurfaceLevel(NativeDensityProgram program, int x, int z, int minY, int maxY, int step) {
        if (!AdrenalineNatives.isAvailable()) {
            return Integer.MIN_VALUE;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.DENSITY);
        try {
            return preliminarySurfaceLevel0(program.bytecode(), program.length(), program.inputValues(), program.inputCount(), program.columnCacheCount(), x, z, minY, maxY, step);
        } finally {
            Reference.reachabilityFence(program);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.DENSITY, startedNanos);
        }
    }

    public static boolean evaluateLattice(NativeDensityProgram program, ByteBuffer inputs, int x, int y, int z,
                                          int width, int height, int stepXz, int stepY, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.DENSITY);
        try {
            return evaluateLattice0(program.bytecode(), program.length(), inputs, program.inputCount(), x, y, z, stepXz, stepY, width, height, output);
        } finally {
            Reference.reachabilityFence(program);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.DENSITY, startedNanos);
        }
    }

    public static boolean evaluateSlice(NativeDensityProgram program, int x, int y, int z, int step, double[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.DENSITY);
        try {
            return evaluateSlice0(program.bytecode(), program.length(), program.inputValues(), program.inputCount(), x, y, z, step, output.length, output);
        } finally {
            Reference.reachabilityFence(program);
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.DENSITY, startedNanos);
        }
    }

    private static native boolean evaluateLattice0(ByteBuffer program, int length, ByteBuffer inputs, int inputCount, int x, int y, int z, int stepXz, int stepY, int width, int height, double[] output);

    private static native boolean evaluateSlice0(ByteBuffer program, int length, ByteBuffer inputs, int inputCount, int x, int y, int z, int step, int count, double[] output);

    private static native int preliminarySurfaceLevel0(ByteBuffer program, int length, ByteBuffer inputs, int inputCount, int cacheCount, int x, int z, int minY, int maxY, int step);
    private static native boolean evaluate0(ByteBuffer program, int length, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, double[] output);
    private static native boolean evaluateWithInputs0(ByteBuffer program, int length, ByteBuffer inputs, int inputCount, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, double[] output);
}
