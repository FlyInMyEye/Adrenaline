package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineCompiledInterpolatorAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public class MixinNoiseInterpolator implements AdrenalineCompiledInterpolatorAccess {

    @Shadow private double noise000;
    @Shadow private double noise001;
    @Shadow private double noise100;
    @Shadow private double noise101;
    @Shadow private double noise010;
    @Shadow private double noise011;
    @Shadow private double noise110;
    @Shadow private double noise111;

    @Override
    public double adrenaline$computeCellValue(DensityFunction.FunctionContext context) {
        MixinNoiseChunkAccessor noiseChunk = (MixinNoiseChunkAccessor) context;
        int cellWidth = noiseChunk.adrenaline$cellWidth();
        int cellHeight = noiseChunk.adrenaline$cellHeight();
        return Mth.lerp3(
            (double) noiseChunk.adrenaline$inCellX() / (double) cellWidth,
            (double) noiseChunk.adrenaline$inCellY() / (double) cellHeight,
            (double) noiseChunk.adrenaline$inCellZ() / (double) cellWidth,
            this.noise000, this.noise100, this.noise010, this.noise110,
            this.noise001, this.noise101, this.noise011, this.noise111);
    }

    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private void adrenaline$fillCellArray(double[] values, DensityFunction.ContextProvider contextProvider, CallbackInfo ci) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()
            || !(contextProvider instanceof MixinNoiseChunkAccessor noiseChunk)
            || !noiseChunk.adrenaline$isFillingCell()) {
            return;
        }

        int cellWidth = noiseChunk.adrenaline$cellWidth();
        int cellHeight = noiseChunk.adrenaline$cellHeight();
        int index = 0;
        for (int inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
            double yLerp = (double) inCellY / (double) cellHeight;
            for (int inCellX = 0; inCellX < cellWidth; inCellX++) {
                double xLerp = (double) inCellX / (double) cellWidth;
                for (int inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                    double zLerp = (double) inCellZ / (double) cellWidth;
                    values[index++] = Mth.lerp3(xLerp, yLerp, zLerp,
                        this.noise000, this.noise100, this.noise010, this.noise110,
                        this.noise001, this.noise101, this.noise011, this.noise111);
                }
            }
        }
        ci.cancel();
    }
}
