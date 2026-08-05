package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.List;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinCacheAllInCellAccessor;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
    @Shadow private Long2IntMap preliminarySurfaceLevel;
    @Shadow private DensityFunction initialDensityNoJaggedness;
    @Shadow private NoiseSettings noiseSettings;

    @Unique
    private AdrenalineMixinNoiseInterpolatorView[] adrenaline$interpolatorArray;

    @Unique
    private AdrenalineMixinCacheAllInCellAccessor[] adrenaline$cellCacheArray;

    @Unique
    private final AdrenalineMutableSinglePointContext adrenaline$singlePointContext = new AdrenalineMutableSinglePointContext();

    @Unique
    private AdrenalineMixinNoiseInterpolatorView[] adrenaline$interpolatorArray() {
        AdrenalineMixinNoiseInterpolatorView[] cached = this.adrenaline$interpolatorArray;
        int size = this.interpolators.size();
        if (cached == null || cached.length != size) {
            cached = new AdrenalineMixinNoiseInterpolatorView[size];
            for (int i = 0; i < size; i++) {
                cached[i] = (AdrenalineMixinNoiseInterpolatorView) this.interpolators.get(i);
            }
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
        AdrenalineMixinNoiseInterpolatorView[] interpolatorArray = this.adrenaline$interpolatorArray();
        for (int cellZ = 0; cellZ < this.cellCountXZ + 1; cellZ++) {
            int currentCellZ = this.firstCellZ + cellZ;
            this.cellStartBlockZ = currentCellZ * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;
            for (int i = 0; i < interpolatorArray.length; i++) {
                AdrenalineMixinNoiseInterpolatorView interpolator = interpolatorArray[i];
                double[] slice = (useFirstSlice ? interpolator.adrenaline$getSlice0() : interpolator.adrenaline$getSlice1())[cellZ];
                interpolator.adrenaline$fillArray(slice, this.sliceFillingContextProvider);
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
        AdrenalineMixinNoiseInterpolatorView[] interpolatorArray = this.adrenaline$interpolatorArray();
        for (int i = 0; i < interpolatorArray.length; i++) {
            action.accept(interpolatorArray[i]);
        }
    }

    @Inject(method = "fillSlice", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$fillSlice(boolean useFirstSlice, int cellX, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$fillSlice(useFirstSlice, cellX);
        ci.cancel();
    }

    @Inject(method = "selectCellYZ", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$selectCellYZ(int cellY, int cellZ, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$forEachInterpolator(interpolator -> interpolator.adrenaline$selectCellYZ(cellY, cellZ));
        this.fillingCell = true;
        this.cellStartBlockY = (cellY + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        this.adrenaline$fillCellCaches();
        this.fillingCell = false;
        ci.cancel();
    }

    @Inject(method = "updateForY", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$updateForY(int blockY, double yLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellY = blockY - this.cellStartBlockY;
        this.adrenaline$forEachInterpolator(interpolator -> interpolator.adrenaline$updateForY(yLerp));
        ci.cancel();
    }

    @Inject(method = "updateForX", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$updateForX(int blockX, double xLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellX = blockX - this.cellStartBlockX;
        this.adrenaline$forEachInterpolator(interpolator -> interpolator.adrenaline$updateForX(xLerp));
        ci.cancel();
    }

    @Inject(method = "updateForZ", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$updateForZ(int blockZ, double zLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        this.adrenaline$forEachInterpolator(interpolator -> interpolator.adrenaline$updateForZ(zLerp));
        ci.cancel();
    }

    @Inject(method = "swapSlices", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$swapSlices(CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$forEachInterpolator(AdrenalineMixinNoiseInterpolatorView::adrenaline$swapSlices);
        ci.cancel();
    }

    @Overwrite
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    public int preliminarySurfaceLevel(int blockX, int blockZ) {
        int x = QuartPos.toBlock(QuartPos.fromBlock(blockX));
        int z = QuartPos.toBlock(QuartPos.fromBlock(blockZ));
        long key = ColumnPos.asLong(x, z);
        int cached = this.preliminarySurfaceLevel.get(key);
        if (cached != 0 || this.preliminarySurfaceLevel.containsKey(key)) {
            return cached;
        }

        int minY = this.noiseSettings.minY();
        AdrenalineMutableSinglePointContext context = this.adrenaline$singlePointContext;
        context.adrenaline$setX(x);
        context.adrenaline$setZ(z);
        for (int y = minY + this.noiseSettings.height(); y >= minY; y -= this.cellHeight) {
            context.adrenaline$setY(y);
            if (this.initialDensityNoJaggedness.compute(context) > 0.390625D) {
                this.preliminarySurfaceLevel.put(key, y);
                return y;
            }
        }

        this.preliminarySurfaceLevel.put(key, Integer.MAX_VALUE);
        return Integer.MAX_VALUE;
    }

    @FunctionalInterface
    private interface InterpolatorAction {
        void accept(AdrenalineMixinNoiseInterpolatorView interpolator);
    }

    @Unique
    private static final class AdrenalineMutableSinglePointContext implements DensityFunction.FunctionContext {

        private int x;
        private int y;
        private int z;

        private void adrenaline$setX(int x) {
            this.x = x;
        }

        private void adrenaline$setY(int y) {
            this.y = y;
        }

        private void adrenaline$setZ(int z) {
            this.z = z;
        }

        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }
}
