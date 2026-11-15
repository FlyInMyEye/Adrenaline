package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.NativeDensitySampler;
import net.fly.adrenaline.worldgen.NativeDensityProgram;
import net.fly.adrenaline.worldgen.NativeDensityProgramCompiler;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunkPreliminarySurface {
    @Shadow private Long2IntMap preliminarySurfaceLevel;
    @Shadow private DensityFunction initialDensityNoJaggedness;
    @Shadow private NoiseSettings noiseSettings;
    @Shadow private int cellHeight;

    @Unique private NativeDensityProgram adrenaline$surfaceProgram;
    @Unique private boolean adrenaline$surfaceProgramInitialized;
    @Unique private final AdrenalineMutableSinglePointContext adrenaline$singlePointContext = new AdrenalineMutableSinglePointContext();

    @WrapMethod(method = "preliminarySurfaceLevel")
    @ControlsOptimization(Optimization.PRELIMINARY_SURFACE)
    private int adrenaline$preliminarySurfaceLevel(int blockX, int blockZ, Operation<Integer> original) {
        if (!AdrenalineConfig.preliminarySurfaceOptimizationsEnabled()) {
            return original.call(blockX, blockZ);
        }
        int x = QuartPos.toBlock(QuartPos.fromBlock(blockX));
        int z = QuartPos.toBlock(QuartPos.fromBlock(blockZ));
        long key = ColumnPos.asLong(x, z);
        int cached = this.preliminarySurfaceLevel.get(key);
        if (cached != 0 || this.preliminarySurfaceLevel.containsKey(key)) {
            return cached;
        }

        int minY = this.noiseSettings.minY();
        int maxY = minY + this.noiseSettings.height();
        AdrenalineMutableSinglePointContext context = this.adrenaline$singlePointContext;
        context.adrenaline$setX(x);
        context.adrenaline$setZ(z);
        if (AdrenalineConfig.nativeDensityEvaluationEnabled()) {
            if (!this.adrenaline$surfaceProgramInitialized) {
                this.adrenaline$surfaceProgramInitialized = true;
                this.adrenaline$surfaceProgram = NativeDensityProgramCompiler.compileColumn(this.initialDensityNoJaggedness).program();
            }
            NativeDensityProgram program = this.adrenaline$surfaceProgram;
            context.adrenaline$setY(maxY);
            if (program != null && program.prepareColumn(context)) {
                int height = NativeDensitySampler.preliminarySurfaceLevel(program, x, z, minY, maxY, this.cellHeight);
                if (height != Integer.MIN_VALUE) {
                    this.preliminarySurfaceLevel.put(key, height);
                    return height;
                }
            }
        }
        for (int y = maxY; y >= minY; y -= this.cellHeight) {
            context.adrenaline$setY(y);
            if (this.initialDensityNoJaggedness.compute(context) > 0.390625D) {
                this.preliminarySurfaceLevel.put(key, y);
                return y;
            }
        }
        this.preliminarySurfaceLevel.put(key, Integer.MAX_VALUE);
        return Integer.MAX_VALUE;
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
