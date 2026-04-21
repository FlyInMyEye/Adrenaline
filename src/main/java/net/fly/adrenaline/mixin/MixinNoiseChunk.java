package net.fly.adrenaline.mixin;

import java.util.List;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinCacheAllInCellAccessor;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorAccessor;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorInvoker;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseChunk.NoiseInterpolator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunk {

    @Shadow private int cellCountXZ;
    @Shadow private List<?> interpolators;
    @Shadow private List<?> cellCaches;
    @Shadow private int cellNoiseMinY;
    @Shadow private int firstCellZ;
    @Shadow private int cellHeight;
    @Shadow private int cellWidth;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int cellStartBlockX;
    @Shadow private int inCellY;
    @Shadow private int inCellX;
    @Shadow private int inCellZ;
    @Shadow private long arrayInterpolationCounter;
    @Shadow private long interpolationCounter;
    @Shadow private boolean fillingCell;
    @Shadow private DensityFunction.ContextProvider sliceFillingContextProvider;

    @Unique
    private Object[] adrenaline$interpolatorArray;

    @Unique
    private AdrenalineMixinCacheAllInCellAccessor[] adrenaline$cellCacheArray;

    @Unique
    private Object[] adrenaline$interpolatorArray() {
        Object[] cached = this.adrenaline$interpolatorArray;
        int size = this.interpolators.size();
        if (cached == null || cached.length != size) {
            cached = this.interpolators.toArray();
            this.adrenaline$interpolatorArray = cached;
        }
        return cached;
    }

    @Unique
    private AdrenalineMixinCacheAllInCellAccessor[] adrenaline$cellCacheArray() {
        AdrenalineMixinCacheAllInCellAccessor[] cached = this.adrenaline$cellCacheArray;
        int size = this.cellCaches.size();
        if (cached == null || cached.length != size) {
            cached = new AdrenalineMixinCacheAllInCellAccessor[size];
            for (int i = 0; i < size; i++) {
                cached[i] = (AdrenalineMixinCacheAllInCellAccessor) this.cellCaches.get(i);
            }
            this.adrenaline$cellCacheArray = cached;
        }
        return cached;
    }

    @Unique
    private void adrenaline$fillSlice(boolean useFirstSlice, int cellX) {
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;
        Object[] interpolatorArray = this.adrenaline$interpolatorArray();
        for (int cellZ = 0; cellZ < this.cellCountXZ + 1; cellZ++) {
            int currentCellZ = this.firstCellZ + cellZ;
            this.cellStartBlockZ = currentCellZ * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;
            for (int i = 0; i < interpolatorArray.length; i++) {
                NoiseInterpolator interpolator = (NoiseInterpolator) interpolatorArray[i];
                AdrenalineMixinNoiseInterpolatorAccessor accessor = (AdrenalineMixinNoiseInterpolatorAccessor) (Object) interpolator;
                double[] slice = (useFirstSlice ? accessor.adrenaline$getSlice0() : accessor.adrenaline$getSlice1())[cellZ];
                interpolator.fillArray(slice, this.sliceFillingContextProvider);
            }
        }
        this.arrayInterpolationCounter++;
    }

    @Unique
    private void adrenaline$fillCellCaches() {
        this.arrayInterpolationCounter++;

        AdrenalineMixinCacheAllInCellAccessor[] cellCacheArray = this.adrenaline$cellCacheArray();
        for (int i = 0; i < cellCacheArray.length; i++) {
            AdrenalineMixinCacheAllInCellAccessor cache = cellCacheArray[i];
            cache.adrenaline$getNoiseFiller().fillArray(cache.adrenaline$getValues(), (NoiseChunk) (Object) this);
        }

        this.arrayInterpolationCounter++;
    }

    @Unique
    private void adrenaline$forEachInterpolator(InterpolatorAction action) {
        Object[] interpolatorArray = this.adrenaline$interpolatorArray();
        for (int i = 0; i < interpolatorArray.length; i++) {
            action.accept(interpolatorArray[i]);
        }
    }

    @Inject(method = "fillSlice", at = @At("HEAD"), cancellable = true)
    private void adrenaline$fillSlice(boolean useFirstSlice, int cellX, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$fillSlice(useFirstSlice, cellX);
        ci.cancel();
    }

    @Inject(method = "selectCellYZ", at = @At("HEAD"), cancellable = true)
    private void adrenaline$selectCellYZ(int cellY, int cellZ, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$forEachInterpolator(interpolator -> ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) interpolator).adrenaline$selectCellYZ(cellY, cellZ));
        this.fillingCell = true;
        this.cellStartBlockY = (cellY + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        this.adrenaline$fillCellCaches();
        this.fillingCell = false;
        ci.cancel();
    }

    @Inject(method = "updateForY", at = @At("HEAD"), cancellable = true)
    private void adrenaline$updateForY(int blockY, double yLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellY = blockY - this.cellStartBlockY;
        this.adrenaline$forEachInterpolator(interpolator -> ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) interpolator).adrenaline$updateForY(yLerp));
        ci.cancel();
    }

    @Inject(method = "updateForX", at = @At("HEAD"), cancellable = true)
    private void adrenaline$updateForX(int blockX, double xLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellX = blockX - this.cellStartBlockX;
        this.adrenaline$forEachInterpolator(interpolator -> ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) interpolator).adrenaline$updateForX(xLerp));
        ci.cancel();
    }

    @Inject(method = "updateForZ", at = @At("HEAD"), cancellable = true)
    private void adrenaline$updateForZ(int blockZ, double zLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        this.adrenaline$forEachInterpolator(interpolator -> ((AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker) interpolator).adrenaline$updateForZ(zLerp));
        ci.cancel();
    }

    @Inject(method = "swapSlices", at = @At("HEAD"), cancellable = true)
    private void adrenaline$swapSlices(CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$forEachInterpolator(interpolator -> ((AdrenalineMixinNoiseInterpolatorInvoker) interpolator).adrenaline$swapSlices());
        ci.cancel();
    }

    @FunctionalInterface
    private interface InterpolatorAction {
        void accept(Object interpolator);
    }
}
