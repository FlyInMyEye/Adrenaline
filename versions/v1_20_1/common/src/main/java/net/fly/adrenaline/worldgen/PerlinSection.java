package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

// TODO: Expose this through AdrenAPI so other mods can use section-backed Perlin sampling.
public final class PerlinSection {

    private static final int APPROXIMATE_TILE_HEIGHT = 16;

    private final NormalNoise noise;
    private final double baseX;
    private final double baseY;
    private final double baseZ;
    private final double xStep;
    private final double yStep;
    private final double zStep;
    private final int xCount;
    private final int yCount;
    private final int zCount;
    private boolean rejected;
    private double[] values;
    private float[][] approximateTiles;

    public PerlinSection(NormalNoise noise, double baseX, double baseY, double baseZ, double xStep, double yStep, double zStep, int xCount, int yCount, int zCount) {
        this.noise = noise;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.xStep = xStep;
        this.yStep = yStep;
        this.zStep = zStep;
        this.xCount = xCount;
        this.yCount = yCount;
        this.zCount = zCount;
    }

    public double valueAt(int xIndex, int yIndex, int zIndex, double x, double y, double z) {
        if (this.rejected
            || xIndex < 0 || xIndex >= this.xCount
            || yIndex < 0 || yIndex >= this.yCount
            || zIndex < 0 || zIndex >= this.zCount
            || !same(x, this.baseX + xIndex * this.xStep)
            || !same(y, this.baseY + yIndex * this.yStep)
            || !same(z, this.baseZ + zIndex * this.zStep)) {
            return Double.NaN;
        }
        if (AdrenalineConfig.approximateNativePerlinEnabled()) {
            return this.approximateValueAt(xIndex, yIndex, zIndex);
        }
        double[] grid = this.exactGrid(xIndex, yIndex, zIndex, x, y, z);
        return grid == null ? Double.NaN : grid[this.valueIndex(xIndex, yIndex, zIndex)];
    }

    public boolean fillColumn(int xIndex, int zIndex, double[] output) {
        if (this.rejected || output.length != this.yCount) {
            return false;
        }
        if (AdrenalineConfig.approximateNativePerlinEnabled()) {
            for (int yIndex = 0; yIndex < this.yCount; yIndex++) {
                double value = this.approximateValueAt(xIndex, yIndex, zIndex);
                if (Double.isNaN(value)) {
                    return false;
                }
                output[yIndex] = value;
            }
            return true;
        }
        double x = this.baseX + xIndex * this.xStep;
        double y = this.baseY;
        double z = this.baseZ + zIndex * this.zStep;
        double[] grid = this.exactGrid(xIndex, 0, zIndex, x, y, z);
        if (grid == null) {
            return false;
        }
        System.arraycopy(grid, this.valueIndex(xIndex, 0, zIndex), output, 0, this.yCount);
        return true;
    }

    private double[] exactGrid(int xIndex, int yIndex, int zIndex, double x, double y, double z) {
        double[] grid = this.values;
        if (grid != null) {
            return grid;
        }
        int sampleCount = this.xCount * this.yCount * this.zCount;
        grid = new double[sampleCount];
        AdrenalineNormalNoiseNativeAccess nativeNoise = (AdrenalineNormalNoiseNativeAccess) this.noise;
        boolean sampled = PerlinNativeSampler.sampleGrid(
            nativeNoise.adrenaline$getFirstNativeData(),
            nativeNoise.adrenaline$getSecondNativeData(),
            nativeNoise.adrenaline$getNativeValueFactor(),
            this.baseX,
            this.baseY,
            this.baseZ,
            this.xStep,
            this.yStep,
            this.zStep,
            this.xCount,
            this.yCount,
            this.zCount,
            grid
        );
        if (!sampled || !same(PerlinBatching.sampleVanilla(() -> this.noise.getValue(x, y, z)), grid[this.valueIndex(xIndex, yIndex, zIndex)])) {
            this.rejected = true;
            return null;
        }
        this.values = grid;
        return grid;
    }

    private int valueIndex(int xIndex, int yIndex, int zIndex) {
        return (xIndex * this.zCount + zIndex) * this.yCount + yIndex;
    }

    private double approximateValueAt(int xIndex, int yIndex, int zIndex) {
        int tileIndex = yIndex / APPROXIMATE_TILE_HEIGHT;
        int tileYStart = tileIndex * APPROXIMATE_TILE_HEIGHT;
        int tileYCount = Math.min(APPROXIMATE_TILE_HEIGHT, this.yCount - tileYStart);
        float[][] tiles = this.approximateTiles;
        if (tiles == null) {
            tiles = new float[(this.yCount + APPROXIMATE_TILE_HEIGHT - 1) / APPROXIMATE_TILE_HEIGHT][];
            this.approximateTiles = tiles;
        }
        float[] tile = tiles[tileIndex];
        if (tile == null) {
            tile = new float[this.xCount * this.zCount * tileYCount];
            AdrenalineNormalNoiseNativeAccess nativeNoise = (AdrenalineNormalNoiseNativeAccess) this.noise;
            if (!PerlinNativeSampler.sampleApproximateGridFloat(
                nativeNoise.adrenaline$getFirstNativeData(),
                nativeNoise.adrenaline$getSecondNativeData(),
                nativeNoise.adrenaline$getNativeValueFactor(),
                this.baseX,
                this.baseY + tileYStart * this.yStep,
                this.baseZ,
                this.xStep,
                this.yStep,
                this.zStep,
                this.xCount,
                tileYCount,
                this.zCount,
                tile
            )) {
                this.rejected = true;
                return Double.NaN;
            }
            tiles[tileIndex] = tile;
        }
        return tile[(xIndex * this.zCount + zIndex) * tileYCount + yIndex - tileYStart];
    }

    private static boolean same(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second)
            || Math.abs(first - second) <= Math.max(1.0E-12D, Math.ulp(second) * 16.0D);
    }
}
