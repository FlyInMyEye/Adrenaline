package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Function;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.BiomeFiddleCache;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.fly.adrenaline.worldgen.SurfaceNoiseBatch;
import net.fly.adrenaline.worldgen.SurfacePointEvaluator;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.BlockColumn;
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
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurfaceSystem.class, priority = SurfacePipelineCompatibility.MIXIN_PRIORITY)
public abstract class MixinSurfaceSystem {

    @Shadow @Final private BlockState defaultBlock;
    @Shadow @Final private NormalNoise surfaceNoise;
    @Shadow @Final private PositionalRandomFactory noiseRandom;

    @WrapMethod(method = "buildSurface")
    private void adrenaline$batchSurfaceSampling(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, Operation<Void> original) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled()) {
            try (SurfacePointEvaluator.Scope scope = SurfacePointEvaluator.suspend()) {
                original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
            }
            return;
        }
        try (SurfaceNoiseBatch batch = new SurfaceNoiseBatch(this.surfaceNoise, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
             BiomeFiddleCache.Scope biomeCache = BiomeFiddleCache.open();
             SurfacePointEvaluator.Scope ruleCache = SurfacePointEvaluator.open(biomeManager)) {
            original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
        }
    }

    @ModifyVariable(method = "buildSurface", at = @At("STORE"), ordinal = 0, require = 0)
    private BlockColumn adrenaline$bufferSurfaceColumn(BlockColumn column,
                                                      @Local(argsOnly = true) ChunkAccess chunk,
                                                      @Local(argsOnly = true) SurfaceRules.RuleSource source,
                                                      @Local(ordinal = 0) MutableBlockPos position) {
        return AdrenalineConfig.surfaceOptimizationsEnabled()
            ? SurfacePointEvaluator.column(column, (SurfaceSystem) (Object) this, chunk, position, this.defaultBlock, source) : column;
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"), require = 0)
    private Holder<Biome> adrenaline$resolveSurfaceBiome(BiomeManager manager, BlockPos position, Operation<Holder<Biome>> original) {
        Holder<Biome> cached = SurfacePointEvaluator.biome(manager, position);
        return cached == null ? original.call(manager, position) : cached;
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;"), require = 0)
    @Coerce
    private Object adrenaline$reuseSurfaceContext(SurfaceSystem system, RandomState randomState,
                                                ChunkAccess chunk, NoiseChunk noiseChunk,
                                                Function<BlockPos, Holder<Biome>> biomeGetter,
                                                Registry<Biome> biomes, WorldGenerationContext generation,
                                                Operation<Object> original,
                                                @Local(argsOnly = true) SurfaceRules.RuleSource source) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled()) {
            return original.call(system, randomState, chunk, noiseChunk, biomeGetter, biomes, generation);
        }
        return SurfacePointEvaluator.context(system, randomState, chunk, noiseChunk, biomeGetter,
            biomes, generation, source, () -> original.call(system, randomState, chunk, noiseChunk, biomeGetter, biomes, generation));
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
    private Object adrenaline$compileSurfaceRules(SurfaceRules.RuleSource source, Object context, Operation<Object> original,
                                                  @Local(argsOnly = true) WorldGenerationContext generation) {
        return AdrenalineConfig.surfaceOptimizationsEnabled()
            ? SurfacePointEvaluator.rule(source, context, generation, () -> original.call(source, context))
            : original.call(source, context);
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;updateY(IIIIII)V"), require = 0)
    private void adrenaline$reuseSurfaceSpan(@Coerce Object context, int above, int below, int water, int x, int y, int z, Operation<Void> original) {
        if (!SurfacePointEvaluator.skipContextUpdate(context, above, below, water, x, y, z)) {
            if (SurfacePointEvaluator.canUpdateDirectly(context)) {
                ((AdrenalineMixinSurfaceRulesContextApi) context).adrenaline$updateY(above, below, water, x, y, z);
            } else {
                original.call(context, above, below, water, x, y, z);
            }
        }
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;"), require = 0)
    private BlockState adrenaline$applySurfaceSpan(@Coerce Object rule, int x, int y, int z, Operation<BlockState> original) {
        BlockState state = SurfacePointEvaluator.canApplyDirectly(rule)
            ? SurfacePointEvaluator.applyDirectly(x, y, z) : original.call(rule, x, y, z);
        return SurfacePointEvaluator.fillSpan(x, y, z, state) ? null : state;
    }

    @ModifyVariable(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER), ordinal = 8, require = 0)
    private int adrenaline$advanceSurfaceDepth(int above) {
        return SurfacePointEvaluator.spanDepth(above);
    }

    @ModifyVariable(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER), ordinal = 12, require = 0)
    private int adrenaline$advanceSurfaceY(int y) {
        return SurfacePointEvaluator.spanY(y);
    }

    @Inject(method = "getSurfaceDepth", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.SURFACE_NOISE)
    private void adrenaline$getSurfaceDepth(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled() || !AdrenalineConfig.surfaceNoiseOptimizationsEnabled()) {
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
