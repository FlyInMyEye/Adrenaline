package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.SurfaceSystemOptimizer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Registry;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurfaceSystem.class)
public abstract class MixinSurfaceSystem {

    @Shadow @Final private BlockState defaultBlock;
    @Shadow @Final private NormalNoise surfaceNoise;

    @Shadow
    protected abstract boolean isStone(BlockState state);

    @Shadow
    protected abstract void erodedBadlandsExtension(net.minecraft.world.level.chunk.BlockColumn column, int blockX, int blockZ, int topY, LevelHeightAccessor heightAccessor);

    @Shadow
    protected abstract void frozenOceanExtension(int minSurfaceLevel, Biome biome, net.minecraft.world.level.chunk.BlockColumn column, MutableBlockPos pos, int blockX, int blockZ, int topY);

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.SURFACE)
    private void adrenaline$buildSurface(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, CallbackInfo ci) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled()) {
            return;
        }

        SurfaceSystemOptimizer.buildSurface((SurfaceSystem) (Object) this, this.defaultBlock, this::isStone, this::erodedBadlandsExtension, this::frozenOceanExtension, randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
        ci.cancel();
    }

    @Inject(method = "getSurfaceDepth", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.SURFACE)
    private void adrenaline$getSurfaceDepth(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled()) {
            return;
        }

        double value = this.surfaceNoise.getValue(blockX, 0.0D, blockZ) * 2.75D + 3.0D;
        long hash = ((long) blockX * 341873128712L) ^ ((long) blockZ * 132897987541L) ^ 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        double jitter = (double) (hash >>> 11) * 1.1102230246251565E-16D * 0.25D;
        cir.setReturnValue(Mth.floor(value + jitter));
    }
}
