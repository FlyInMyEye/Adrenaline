package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
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
