package net.fly.adrenaline.mixin;

import java.util.List;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinCacheAllInCellAccessor;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;
import net.fly.adrenaline.worldgen.AdrenalineCellGridAccess;
import net.fly.adrenaline.worldgen.AdrenalineNoiseChunkCoordinateAccess;
import net.fly.adrenaline.worldgen.AdrenalineNoiseChunkMaterialAccess;
import net.fly.adrenaline.worldgen.AdrenalineSectionNoiseAccess;
import net.fly.adrenaline.worldgen.CellDensityCompiler;
import net.fly.adrenaline.worldgen.CellDensityEvaluator;
import net.fly.adrenaline.worldgen.NoiseInterpolationKernel;
import net.fly.adrenaline.worldgen.NativeCellDensityEvaluator;
import net.fly.adrenaline.worldgen.NativeSliceLattice;
import net.fly.adrenaline.worldgen.PerlinBatching;
import net.fly.adrenaline.worldgen.PerlinSectionCache;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunkCells implements AdrenalineCellGridAccess, AdrenalineNoiseChunkCoordinateAccess, AdrenalineSectionNoiseAccess {
    @Shadow private int cellCountXZ;
    @Shadow private int cellCountY;
    @Shadow private DensityFunction.ContextProvider sliceFillingContextProvider;
    @Shadow private int cellNoiseMinY;
    @Shadow private int cellWidth;
    @Shadow private int cellHeight;
    @Shadow private int firstCellX;
    @Shadow private int firstCellZ;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long arrayInterpolationCounter;
    @Shadow private long interpolationCounter;
    @Shadow private boolean fillingCell;
    @Shadow private List<?> interpolators;
    @Shadow private List<?> cellCaches;

    @Override
    public boolean adrenaline$supportsSectionTraversal(int minCellY, int cellCountY) {
        if (this.cellWidth != 4 || this.cellHeight != 8 || this.cellCountXZ != 4
            || this.cellNoiseMinY != minCellY || this.cellCountY != cellCountY) {
            return false;
        }
        for (CellDensityEvaluator evaluator : this.adrenaline$cellDensityEvaluators(this.adrenaline$cellCacheArray())) {
            if (!(evaluator instanceof NativeCellDensityEvaluator)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void adrenaline$setSectionCellX(int cellX) {
        this.cellStartBlockX = (this.firstCellX + cellX) * this.cellWidth;
    }

    @Override
    public void adrenaline$updateYCoordinate(int blockY) {
        this.inCellY = blockY - this.cellStartBlockY;
    }

    @Override
    public void adrenaline$updateXCoordinate(int blockX) {
        this.inCellX = blockX - this.cellStartBlockX;
    }

    @Override
    public void adrenaline$updateZCoordinate(int blockZ) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
    }

    @Override
    public int adrenaline$getCellStartBlockX() {
        return this.cellStartBlockX;
    }

    @Override
    public int adrenaline$getCellStartBlockY() {
        return this.cellStartBlockY;
    }

    @Override
    public int adrenaline$getCellStartBlockZ() {
        return this.cellStartBlockZ;
    }

    @Override
    public int adrenaline$getCellWidth() {
        return this.cellWidth;
    }

    @Override
    public int adrenaline$getCellHeight() {
        return this.cellHeight;
    }

    @Unique
    private AdrenalineMixinNoiseInterpolatorView[] adrenaline$interpolatorArray;

    @Unique
    private AdrenalineMixinCacheAllInCellAccessor[] adrenaline$cellCacheArray;

    @Unique
    private CellDensityEvaluator[] adrenaline$cellDensityEvaluators;

    @Unique private CellDensityEvaluator[] adrenaline$sliceDensityEvaluators;
    @Unique private PerlinSectionCache adrenaline$perlinSections;
    @Unique private double[][] adrenaline$sliceLattices;
    @Unique private int adrenaline$perlinSectionBaseCellX = Integer.MIN_VALUE;

    @Unique private double[] adrenaline$noise000;
    @Unique private double[] adrenaline$noise001;
    @Unique private double[] adrenaline$noise100;
    @Unique private double[] adrenaline$noise101;
    @Unique private double[] adrenaline$noise010;
    @Unique private double[] adrenaline$noise011;
    @Unique private double[] adrenaline$noise110;
    @Unique private double[] adrenaline$noise111;
    @Unique private double[] adrenaline$valueXZ00;
    @Unique private double[] adrenaline$valueXZ01;
    @Unique private double[] adrenaline$valueXZ10;
    @Unique private double[] adrenaline$valueXZ11;
    @Unique private double[] adrenaline$valueZ0;
    @Unique private double[] adrenaline$valueZ1;
    @Unique private double[] adrenaline$values;

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
            this.adrenaline$initializeInterpolationArrays(size);
        }
        return cached;
    }

    @Unique
    private void adrenaline$initializeInterpolationArrays(int size) {
        this.adrenaline$noise000 = new double[size];
        this.adrenaline$noise001 = new double[size];
        this.adrenaline$noise100 = new double[size];
        this.adrenaline$noise101 = new double[size];
        this.adrenaline$noise010 = new double[size];
        this.adrenaline$noise011 = new double[size];
        this.adrenaline$noise110 = new double[size];
        this.adrenaline$noise111 = new double[size];
        this.adrenaline$valueXZ00 = new double[size];
        this.adrenaline$valueXZ01 = new double[size];
        this.adrenaline$valueXZ10 = new double[size];
        this.adrenaline$valueXZ11 = new double[size];
        this.adrenaline$valueZ0 = new double[size];
        this.adrenaline$valueZ1 = new double[size];
        this.adrenaline$values = new double[size];
        this.adrenaline$sliceDensityEvaluators = null;
        this.adrenaline$perlinSections = null;
        this.adrenaline$sliceLattices = null;
        this.adrenaline$perlinSectionBaseCellX = Integer.MIN_VALUE;
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
    private CellDensityEvaluator[] adrenaline$cellDensityEvaluators(AdrenalineMixinCacheAllInCellAccessor[] cellCaches) {
        CellDensityEvaluator[] cached = this.adrenaline$cellDensityEvaluators;
        if (cached == null || cached.length != cellCaches.length) {
            cached = new CellDensityEvaluator[cellCaches.length];
            for (int i = 0; i < cellCaches.length; i++) {
                cached[i] = CellDensityCompiler.compile(cellCaches[i].adrenaline$getNoiseFiller());
            }
            this.adrenaline$cellDensityEvaluators = cached;
        }
        return cached;
    }

    @Unique
    private CellDensityEvaluator[] adrenaline$sliceDensityEvaluators(AdrenalineMixinNoiseInterpolatorView[] interpolators) {
        CellDensityEvaluator[] cached = this.adrenaline$sliceDensityEvaluators;
        if (cached == null || cached.length != interpolators.length) {
            cached = new CellDensityEvaluator[interpolators.length];
            for (int i = 0; i < interpolators.length; i++) {
                cached[i] = CellDensityCompiler.compileSlice(interpolators[i].adrenaline$getNoiseFiller());
            }
            this.adrenaline$sliceDensityEvaluators = cached;
        }
        return cached;
    }

    @Unique
    private void adrenaline$fillSlice(boolean useFirstSlice, int cellX) {
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;
        AdrenalineMixinNoiseInterpolatorView[] interpolatorArray = this.adrenaline$interpolatorArray();
        CellDensityEvaluator[] sliceEvaluators = this.adrenaline$sliceDensityEvaluators(interpolatorArray);
        boolean nativePerlin = AdrenalineConfig.nativePerlinBatchingEnabled();
        boolean nativeSection = nativePerlin || AdrenalineConfig.nativeBlendedNoiseEnabled();
        if (nativeSection && this.adrenaline$perlinSectionBaseCellX == Integer.MIN_VALUE) {
            this.adrenaline$perlinSectionBaseCellX = cellX;
            this.adrenaline$perlinSections = new PerlinSectionCache(
                cellX * this.cellWidth,
                this.cellNoiseMinY * this.cellHeight,
                this.firstCellZ * this.cellWidth,
                this.cellWidth,
                this.cellHeight,
                this.cellCountXZ + 1,
                this.cellCountY + 1,
                this.cellCountXZ + 1
            );
        }
        boolean nativeLattice = nativePerlin && AdrenalineConfig.nativeBlendedNoiseEnabled() && AdrenalineConfig.nativeDensityEvaluationEnabled();
        if (nativeLattice && this.adrenaline$sliceLattices == null) {
            this.adrenaline$sliceLattices = new double[interpolatorArray.length][];
            for (int i = 0; i < sliceEvaluators.length; i++) {
                if (sliceEvaluators[i] instanceof NativeCellDensityEvaluator evaluator) {
                    this.adrenaline$sliceLattices[i] = NativeSliceLattice.evaluate(evaluator.program(), (NoiseChunk) (Object) this,
                        this.adrenaline$perlinSections, this.cellStartBlockX, this.cellNoiseMinY * this.cellHeight, this.firstCellZ * this.cellWidth,
                        this.cellCountXZ + 1, this.cellCountY + 1, this.cellWidth, this.cellHeight);
                }
            }
        }
        int perlinXIndex = cellX - this.adrenaline$perlinSectionBaseCellX;
        for (int cellZ = 0; cellZ < this.cellCountXZ + 1; cellZ++) {
            int currentCellZ = this.firstCellZ + cellZ;
            this.cellStartBlockZ = currentCellZ * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;
            for (int i = 0; i < interpolatorArray.length; i++) {
                AdrenalineMixinNoiseInterpolatorView interpolator = interpolatorArray[i];
                double[] slice = (useFirstSlice ? interpolator.adrenaline$getSlice0() : interpolator.adrenaline$getSlice1())[cellZ];
                if (nativeLattice && perlinXIndex >= 0 && perlinXIndex <= this.cellCountXZ && this.adrenaline$sliceLattices[i] != null) {
                    double[] lattice = this.adrenaline$sliceLattices[i];
                    System.arraycopy(lattice, (perlinXIndex * (this.cellCountXZ + 1) + cellZ) * slice.length, slice, 0, slice.length);
                    this.sliceFillingContextProvider.forIndex(slice.length - 1);
                    continue;
                }
                if (nativeSection) {
                    PerlinBatching.enterSection(this.adrenaline$perlinSections, perlinXIndex, cellZ, this.sliceFillingContextProvider);
                }
                try {
                    CellDensityEvaluator evaluator = sliceEvaluators[i];
                    if (evaluator == null) {
                        interpolator.adrenaline$fillArray(slice, this.sliceFillingContextProvider);
                    } else {
                        if (evaluator instanceof NativeCellDensityEvaluator nativeEvaluator
                            && nativeEvaluator.fillSlice(slice, this.sliceFillingContextProvider, this.cellStartBlockX,
                                this.cellNoiseMinY * this.cellHeight, this.cellStartBlockZ, this.cellHeight)) {
                            continue;
                        }
                        if (nativePerlin) {
                            PerlinBatching.begin(slice.length);
                        }
                        try {
                            evaluator.fill(slice, this.sliceFillingContextProvider);
                        } finally {
                            if (nativePerlin) {
                                PerlinBatching.end();
                            }
                        }
                    }
                } finally {
                    if (nativeSection) {
                        PerlinBatching.exitSection();
                    }
                }
            }
        }
        this.arrayInterpolationCounter++;
    }

    @Inject(method = "fillSlice", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_SLICES)
    private void adrenaline$fillSlice(boolean useFirstSlice, int cellX, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseSliceOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$fillSlice(useFirstSlice, cellX);
        ci.cancel();
    }

    @Unique
    private void adrenaline$fillCellCaches() {
        this.arrayInterpolationCounter++;

        AdrenalineMixinCacheAllInCellAccessor[] cellCacheArray = this.adrenaline$cellCacheArray();
        CellDensityEvaluator[] evaluators = this.adrenaline$cellDensityEvaluators(cellCacheArray);
        for (int i = 0; i < cellCacheArray.length; i++) {
            AdrenalineMixinCacheAllInCellAccessor cache = cellCacheArray[i];
            CellDensityEvaluator evaluator = evaluators[i];
            if (evaluator == null) {
                cache.adrenaline$getNoiseFiller().fillArray(cache.adrenaline$getValues(), (NoiseChunk) (Object) this);
            } else {
                evaluator.fill(cache.adrenaline$getValues(), (NoiseChunk) (Object) this);
            }
        }

        ((AdrenalineNoiseChunkMaterialAccess) this).adrenaline$fillMaterialArrays((NoiseChunk) (Object) this);
        this.arrayInterpolationCounter++;
    }

    @Unique
    private void adrenaline$forEachInterpolator(InterpolatorAction action) {
        AdrenalineMixinNoiseInterpolatorView[] interpolatorArray = this.adrenaline$interpolatorArray();
        for (int i = 0; i < interpolatorArray.length; i++) {
            action.accept(interpolatorArray[i]);
        }
    }

    @Inject(method = "selectCellYZ", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization({Optimization.CELL_CACHE, Optimization.INTERPOLATION})
    private void adrenaline$selectCellYZ(int cellY, int cellZ, CallbackInfo ci) {
        if (!AdrenalineConfig.cellCacheOptimizationsEnabled() && !AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        AdrenalineMixinNoiseInterpolatorView[] interpolators = this.adrenaline$interpolatorArray();
        for (int index = 0; index < interpolators.length; index++) {
            AdrenalineMixinNoiseInterpolatorView interpolator = interpolators[index];
            interpolator.adrenaline$selectCellYZ(cellY, cellZ);
            double[][] slice0 = interpolator.adrenaline$getSlice0();
            double[][] slice1 = interpolator.adrenaline$getSlice1();
            this.adrenaline$noise000[index] = slice0[cellZ][cellY];
            this.adrenaline$noise001[index] = slice0[cellZ + 1][cellY];
            this.adrenaline$noise100[index] = slice1[cellZ][cellY];
            this.adrenaline$noise101[index] = slice1[cellZ + 1][cellY];
            this.adrenaline$noise010[index] = slice0[cellZ][cellY + 1];
            this.adrenaline$noise011[index] = slice0[cellZ + 1][cellY + 1];
            this.adrenaline$noise110[index] = slice1[cellZ][cellY + 1];
            this.adrenaline$noise111[index] = slice1[cellZ + 1][cellY + 1];
        }
        this.fillingCell = true;
        this.cellStartBlockY = (cellY + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        if (AdrenalineConfig.cellCacheOptimizationsEnabled()) {
            this.adrenaline$fillCellCaches();
        } else {
            this.arrayInterpolationCounter++;
            for (Object entry : this.cellCaches) {
                AdrenalineMixinCacheAllInCellAccessor cache = (AdrenalineMixinCacheAllInCellAccessor) entry;
                cache.adrenaline$getNoiseFiller().fillArray(cache.adrenaline$getValues(), (NoiseChunk) (Object) this);
            }
            this.arrayInterpolationCounter++;
        }
        this.fillingCell = false;
        ci.cancel();
    }

    @Inject(method = "updateForY", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.INTERPOLATION)
    private void adrenaline$updateForY(int blockY, double yLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellY = blockY - this.cellStartBlockY;
        this.adrenaline$interpolatorArray();
        NoiseInterpolationKernel.interpolateY(yLerp, this.adrenaline$noise000, this.adrenaline$noise001, this.adrenaline$noise100, this.adrenaline$noise101, this.adrenaline$noise010, this.adrenaline$noise011, this.adrenaline$noise110, this.adrenaline$noise111, this.adrenaline$valueXZ00, this.adrenaline$valueXZ01, this.adrenaline$valueXZ10, this.adrenaline$valueXZ11);
        ci.cancel();
    }

    @Inject(method = "updateForX", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.INTERPOLATION)
    private void adrenaline$updateForX(int blockX, double xLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellX = blockX - this.cellStartBlockX;
        this.adrenaline$interpolatorArray();
        NoiseInterpolationKernel.interpolateX(xLerp, this.adrenaline$valueXZ00, this.adrenaline$valueXZ01, this.adrenaline$valueXZ10, this.adrenaline$valueXZ11, this.adrenaline$valueZ0, this.adrenaline$valueZ1);
        ci.cancel();
    }

    @Inject(method = "updateForZ", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.INTERPOLATION)
    private void adrenaline$updateForZ(int blockZ, double zLerp, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        AdrenalineMixinNoiseInterpolatorView[] interpolators = this.adrenaline$interpolatorArray();
        NoiseInterpolationKernel.interpolateZ(zLerp, this.adrenaline$valueZ0, this.adrenaline$valueZ1, this.adrenaline$values);
        for (int index = 0; index < interpolators.length; index++) {
            interpolators[index].adrenaline$setValue(this.adrenaline$values[index]);
        }
        ci.cancel();
    }

    @Inject(method = "swapSlices", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.INTERPOLATION)
    private void adrenaline$swapSlices(CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return;
        }

        this.adrenaline$forEachInterpolator(AdrenalineMixinNoiseInterpolatorView::adrenaline$swapSlices);
        ci.cancel();
    }

    @FunctionalInterface
    private interface InterpolatorAction {
        void accept(AdrenalineMixinNoiseInterpolatorView interpolator);
    }
}
