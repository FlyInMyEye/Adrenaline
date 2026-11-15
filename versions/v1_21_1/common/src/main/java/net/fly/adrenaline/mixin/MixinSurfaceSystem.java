package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.SurfaceNoiseBatch;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurfaceSystem.class, priority = 1100)
public abstract class MixinSurfaceSystem {
    @Shadow @Final private NormalNoise surfaceNoise;
    @Shadow @Final private PositionalRandomFactory noiseRandom;

    @WrapMethod(method = "buildSurface")
    private void adrenaline$batchSurfaceSampling(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, Operation<Void> original) {
        if (!AdrenalineConfig.surfaceNoiseOptimizationsEnabled() && !AdrenalineConfig.surfaceBandNoiseOptimizationsEnabled()) {
            original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
            return;
        }
        try (SurfaceNoiseBatch batch = new SurfaceNoiseBatch(this.surfaceNoise, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
        }
    }

    @Inject(method = "getSurfaceDepth", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.SURFACE_DEPTH_NOISE)
    private void adrenaline$getSurfaceDepth(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {
        if (!AdrenalineConfig.surfaceNoiseOptimizationsEnabled()) {
            return;
        }

        double value = SurfaceNoiseBatch.sample(this.surfaceNoise, blockX, blockZ) * 2.75D + 3.0D;
        double jitter = this.noiseRandom.at(blockX, 0, blockZ).nextDouble() * 0.25D;
        double depth = value + jitter;
        if (Math.abs(depth - Math.rint(depth)) < 1.0E-9D) {
            depth = this.surfaceNoise.getValue(blockX, 0.0D, blockZ) * 2.75D + 3.0D + jitter;
        }
        cir.setReturnValue((int) depth);
    }
}
