package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import net.fly.adrenaline.natives.BlendedNativeSampler;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class PerlinSectionCache {

    private final int baseBlockX;
    private final int baseBlockY;
    private final int baseBlockZ;
    private final int cellWidth;
    private final int cellHeight;
    private final int xCount;
    private final int yCount;
    private final int zCount;
    private final IdentityHashMap<Object, PerlinSection> sections = new IdentityHashMap<>();
    private final IdentityHashMap<BlendedNoise, double[]> blendedSections = new IdentityHashMap<>();

    public PerlinSectionCache(int baseBlockX, int baseBlockY, int baseBlockZ, int cellWidth, int cellHeight, int xCount, int yCount, int zCount) {
        this.baseBlockX = baseBlockX;
        this.baseBlockY = baseBlockY;
        this.baseBlockZ = baseBlockZ;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.xCount = xCount;
        this.yCount = yCount;
        this.zCount = zCount;
    }

    public double valueAt(Object key, NormalNoise noise, double xzScale, double yScale, int blockX, int blockY, int blockZ, int xIndex, int zIndex) {
        if (blockX != this.baseBlockX + xIndex * this.cellWidth || blockZ != this.baseBlockZ + zIndex * this.cellWidth) {
            return Double.NaN;
        }
        int yOffset = blockY - this.baseBlockY;
        if (yOffset % this.cellHeight != 0) {
            return Double.NaN;
        }
        int yIndex = yOffset / this.cellHeight;
        if (yIndex < 0 || yIndex >= this.yCount) {
            return Double.NaN;
        }
        PerlinSection section = this.section(key, noise, xzScale, yScale);
        return section.valueAt(xIndex, yIndex, zIndex, blockX * xzScale, blockY * yScale, blockZ * xzScale);
    }

    public boolean fillColumn(Object key, NormalNoise noise, double xzScale, double yScale, int xIndex, int zIndex, double[] values) {
        return xIndex >= 0 && xIndex < this.xCount
            && zIndex >= 0 && zIndex < this.zCount
            && values.length == this.yCount
            && this.section(key, noise, xzScale, yScale).fillColumn(xIndex, zIndex, values);
    }

    public double valueAt(BlendedNoise noise, int blockX, int blockY, int blockZ, int xIndex, int zIndex) {
        if (xIndex < 0 || xIndex >= this.xCount || zIndex < 0 || zIndex >= this.zCount
            || blockX != this.baseBlockX + xIndex * this.cellWidth || blockZ != this.baseBlockZ + zIndex * this.cellWidth) {
            return Double.NaN;
        }
        int yOffset = blockY - this.baseBlockY;
        int yIndex = yOffset / this.cellHeight;
        if (yOffset % this.cellHeight != 0 || yIndex < 0 || yIndex >= this.yCount) {
            return Double.NaN;
        }
        double[] values = this.blendedGrid(noise);
        return values.length == 0 ? Double.NaN : values[(xIndex * this.zCount + zIndex) * this.yCount + yIndex];
    }

    public boolean fillGrid(Object key, NormalNoise noise, double xzScale, double yScale, double[] output) {
        return this.section(key, noise, xzScale, yScale).fillGrid(output);
    }

    public boolean fillGrid(BlendedNoise noise, double[] output) {
        double[] grid = this.blendedGrid(noise);
        if (grid.length == 0 || grid.length != output.length) {
            return false;
        }
        System.arraycopy(grid, 0, output, 0, output.length);
        return true;
    }

    private double[] blendedGrid(BlendedNoise noise) {
        double[] values = this.blendedSections.get(noise);
        if (values == null) {
            values = new double[this.xCount * this.yCount * this.zCount];
            if (!BlendedNativeSampler.sampleGrid(((AdrenalineBlendedNoiseAccess) noise).adrenaline$getNativeData(),
                this.baseBlockX, this.baseBlockY, this.baseBlockZ, this.cellWidth, this.cellHeight, this.cellWidth,
                this.xCount, this.yCount, this.zCount, values)) {
                values = new double[0];
            }
            this.blendedSections.put(noise, values);
        }
        return values;
    }

    private PerlinSection section(Object key, NormalNoise noise, double xzScale, double yScale) {
        PerlinSection section = this.sections.get(key);
        if (section == null) {
            section = new PerlinSection(
                noise,
                this.baseBlockX * xzScale,
                this.baseBlockY * yScale,
                this.baseBlockZ * xzScale,
                this.cellWidth * xzScale,
                this.cellHeight * yScale,
                this.cellWidth * xzScale,
                this.xCount,
                this.yCount,
                this.zCount
            );
            this.sections.put(key, section);
        }
        return section;
    }
}
