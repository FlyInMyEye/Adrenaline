package net.fly.adrenaline.natives;

public final class NativeAquiferSampler {

    private NativeAquiferSampler() {
    }

    public static boolean evaluate(double[] densityValues, double[] barrierValues, long[] candidates, int[] fluidLevels, byte[] fluidTypes,
                                   int globalFluidLevel, byte globalFluidType, int baseY, int cellWidth, int cellHeight, int[] deferredIndices, int deferredCount, byte[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.AQUIFER_FINAL);
        try {
            return evaluate0(densityValues, barrierValues, candidates, fluidLevels, fluidTypes, globalFluidLevel, globalFluidType, baseY, cellWidth, cellHeight, deferredIndices, deferredCount, output);
        } finally {
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.AQUIFER_FINAL, startedNanos);
        }
    }

    public static boolean locate(double[] densityValues, byte[] globalMaterials, long[] candidates, short[] packedLocations,
                                 int minGridX, int minGridY, int minGridZ, int gridSizeX, int gridSizeZ,
                                 int baseX, int baseY, int baseZ, int cellWidth, int cellHeight) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        return locate0(densityValues, globalMaterials, candidates, packedLocations,
            minGridX, minGridY, minGridZ, gridSizeX, gridSizeZ, baseX, baseY, baseZ, cellWidth, cellHeight);
    }

    public static boolean prepare(double[] densityValues, long[] candidates, short[] packedLocations, int[] fluidLevels, byte[] fluidTypes, int globalFluidLevel, byte globalFluidType,
                                  int minGridX, int minGridY, int minGridZ, int gridSizeX, int gridSizeZ,
                                  int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, int[] deferredIndices, int[] deferredCount, byte[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        long startedNanos = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.AQUIFER_PREPARE);
        try {
            return prepare0(densityValues, candidates, packedLocations, fluidLevels, fluidTypes, globalFluidLevel, globalFluidType,
                minGridX, minGridY, minGridZ, gridSizeX, gridSizeZ, baseX, baseY, baseZ, cellWidth, cellHeight, deferredIndices, deferredCount, output);
        } finally {
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.AQUIFER_PREPARE, startedNanos);
        }
    }

    public static boolean classify(double[] densityValues, byte[] globalMaterials, long[] candidates, int[] fluidLevels, byte[] fluidTypes,
                                   int baseY, int cellWidth, int cellHeight, byte[] output) {
        if (!AdrenalineNatives.isAvailable()) {
            return false;
        }
        return classify0(densityValues, globalMaterials, candidates, fluidLevels, fluidTypes, baseY, cellWidth, cellHeight, output);
    }

    private static native boolean evaluate0(double[] densityValues, double[] barrierValues, long[] candidates, int[] fluidLevels, byte[] fluidTypes,
                                            int globalFluidLevel, byte globalFluidType, int baseY, int cellWidth, int cellHeight, int[] deferredIndices, int deferredCount, byte[] output);

    private static native boolean locate0(double[] densityValues, byte[] globalMaterials, long[] candidates, short[] packedLocations,
                                          int minGridX, int minGridY, int minGridZ, int gridSizeX, int gridSizeZ,
                                          int baseX, int baseY, int baseZ, int cellWidth, int cellHeight);

    private static native boolean prepare0(double[] densityValues, long[] candidates, short[] packedLocations, int[] fluidLevels, byte[] fluidTypes, int globalFluidLevel, byte globalFluidType,
                                           int minGridX, int minGridY, int minGridZ, int gridSizeX, int gridSizeZ,
                                           int baseX, int baseY, int baseZ, int cellWidth, int cellHeight, int[] deferredIndices, int[] deferredCount, byte[] output);

    private static native boolean classify0(double[] densityValues, byte[] globalMaterials, long[] candidates, int[] fluidLevels, byte[] fluidTypes,
                                            int baseY, int cellWidth, int cellHeight, byte[] output);
}
