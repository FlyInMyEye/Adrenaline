package net.fly.adrenaline.mixin;

import java.util.List;

import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinCacheAllInCellAccessor;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunk {

    @Shadow private List<?> interpolators;
    @Shadow private List<?> cellCaches;
    @Shadow private int cellNoiseMinY;
    @Shadow private int firstCellZ;
    @Shadow private int cellHeight;
    @Shadow private int cellWidth;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellY;
    @Shadow private int cellStartBlockX;
    @Shadow private int inCellX;
    @Shadow private int inCellZ;
    @Shadow private long arrayInterpolationCounter;
    @Shadow private long interpolationCounter;
    @Shadow private boolean fillingCell;

    /**
     * @author Fly
     * @reason Remove list lambda dispatch in hot loop
     */
    @Overwrite
    public void selectCellYZ(int cellY, int cellZ) {
        int interpolatorCount = this.interpolators.size();
        for (int i = 0; i < interpolatorCount; i++) {
            ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) this.interpolators.get(i)).adrenaline$selectCellYZ(cellY, cellZ);
        }

        this.fillingCell = true;
        this.cellStartBlockY = (cellY + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        this.arrayInterpolationCounter++;

        int cacheCount = this.cellCaches.size();
        for (int i = 0; i < cacheCount; i++) {
            Object cache = this.cellCaches.get(i);
            AdrenalineMixinCacheAllInCellAccessor acc = (AdrenalineMixinCacheAllInCellAccessor) cache;
            acc.getNoiseFiller().fillArray(acc.getValues(), (NoiseChunk) (Object) this);
        }

        this.arrayInterpolationCounter++;
        this.fillingCell = false;
    }

    /**
     * @author Fly
     * @reason Remove list lambda dispatch in hot loop
     */
    @Overwrite
    public void updateForY(int blockY, double yLerp) {
        this.inCellY = blockY - this.cellStartBlockY;

        int interpolatorCount = this.interpolators.size();
        for (int i = 0; i < interpolatorCount; i++) {
            ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) this.interpolators.get(i)).adrenaline$updateForY(yLerp);
        }
    }

    /**
     * @author Fly
     * @reason Remove list lambda dispatch in hot loop
     */
    @Overwrite
    public void updateForX(int blockX, double xLerp) {
        this.inCellX = blockX - this.cellStartBlockX;

        int interpolatorCount = this.interpolators.size();
        for (int i = 0; i < interpolatorCount; i++) {
            ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) this.interpolators.get(i)).adrenaline$updateForX(xLerp);
        }
    }

    /**
     * @author Fly
     * @reason Remove list lambda dispatch in hot loop
     */
    @Overwrite
    public void updateForZ(int blockZ, double zLerp) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;

        int interpolatorCount = this.interpolators.size();
        for (int i = 0; i < interpolatorCount; i++) {
            ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) this.interpolators.get(i)).adrenaline$updateForZ(zLerp);
        }
    }

    /**
     * @author Fly
     * @reason Remove list lambda dispatch in hot loop
     */
    @Overwrite
    public void swapSlices() {
        int interpolatorCount = this.interpolators.size();
        for (int i = 0; i < interpolatorCount; i++) {
            ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) this.interpolators.get(i)).adrenaline$swapSlices();
        }
    }
}
