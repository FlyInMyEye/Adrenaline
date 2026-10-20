package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.function.Function;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.BiomeFiddleCache;
import net.fly.adrenaline.worldgen.SurfaceNoiseBatch;
import net.fly.adrenaline.worldgen.SurfacePointEvaluator;
import net.fly.adrenaline.worldgen.NativeSurfacePrograms;
import net.fly.adrenaline.worldgen.AdrenalineNativeSurfaceProgramsAccess;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurfaceSystem.class, priority = SurfacePipelineCompatibility.MIXIN_PRIORITY)
public abstract class MixinSurfaceSystem implements AdrenalineNativeSurfaceProgramsAccess {

    @Unique private final NativeSurfacePrograms adrenaline$surfacePrograms = new NativeSurfacePrograms();

    @Override
    public NativeSurfacePrograms adrenaline$getSurfacePrograms() {
        return this.adrenaline$surfacePrograms;
    }

    @Shadow @Final private BlockState defaultBlock;
    @Shadow @Final private NormalNoise surfaceNoise;
    @Shadow @Final private PositionalRandomFactory noiseRandom;

    @WrapMethod(method = "buildSurface")
    private void adrenaline$batchSurfaceSampling(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, Operation<Void> original) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled() && !AdrenalineConfig.nativeSurfaceRulesEnabled()
            && !AdrenalineConfig.surfaceContextOptimizationsEnabled() && !AdrenalineConfig.surfaceColumnOptimizationsEnabled()
            && !AdrenalineConfig.surfaceBiomeOptimizationsEnabled() && !AdrenalineConfig.surfaceNoiseOptimizationsEnabled()
            && !AdrenalineConfig.surfaceBandNoiseOptimizationsEnabled()) {
            try (SurfacePointEvaluator.Scope scope = SurfacePointEvaluator.suspend()) {
                original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
            }
            return;
        }
        try (SurfaceNoiseBatch batch = new SurfaceNoiseBatch(this.surfaceNoise, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
             BiomeFiddleCache.Scope biomeCache = BiomeFiddleCache.open();
             SurfacePointEvaluator.Scope ruleCache = SurfacePointEvaluator.open(biomeManager, (SurfaceSystem) (Object) this)) {
            original.call(randomState, biomeManager, biomeRegistry, useLegacyRandomSource, context, chunk, noiseChunk, ruleSource);
        }
    }

    @ModifyVariable(method = "buildSurface", at = @At("STORE"), ordinal = 0, require = 0)
    @ControlsOptimization(value = Optimization.SURFACE_COLUMN, local = true)
    private BlockColumn adrenaline$bufferSurfaceColumn(BlockColumn column,
                                                      @Local(argsOnly = true) ChunkAccess chunk,
                                                      @Local(argsOnly = true) SurfaceRules.RuleSource source,
                                                      @Local(ordinal = 0) MutableBlockPos position) {
        return AdrenalineConfig.surfaceColumnOptimizationsEnabled()
            ? SurfacePointEvaluator.column(column, (SurfaceSystem) (Object) this, chunk, position, this.defaultBlock, source) : column;
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;"), require = 0)
    @Coerce
    @ControlsOptimization(value = {Optimization.SURFACE_CONTEXT, Optimization.SURFACE_BIOMES}, local = true)
    private Object adrenaline$reuseSurfaceContext(SurfaceSystem system, RandomState randomState,
                                                ChunkAccess chunk, NoiseChunk noiseChunk,
                                                Function<BlockPos, Holder<Biome>> biomeGetter,
                                                Registry<Biome> biomes, WorldGenerationContext generation,
                                                Operation<Object> original,
                                                @Local(argsOnly = true) SurfaceRules.RuleSource source) {
        return SurfacePointEvaluator.context(system, randomState, chunk, noiseChunk, biomeGetter,
            biomes, generation, source, () -> original.call(system, randomState, chunk, noiseChunk, biomeGetter, biomes, generation));
    }

    @WrapOperation(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
    @ControlsOptimization(value = {Optimization.SURFACE_RULES, Optimization.NATIVE_SURFACE_RULES, Optimization.SURFACE_SPANS}, local = true)
    private Object adrenaline$compileSurfaceRules(SurfaceRules.RuleSource source, Object context, Operation<Object> original,
                                                  @Local(argsOnly = true) WorldGenerationContext generation,
                                                  @Share("surfaceSpans") LocalRef<SurfacePointEvaluator.Evaluator> spans) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled() && !AdrenalineConfig.nativeSurfaceRulesEnabled()
            && !AdrenalineConfig.surfaceContextOptimizationsEnabled()) {
            return original.call(source, context);
        }
        Object rule = SurfacePointEvaluator.rule(source, context, generation, () -> original.call(source, context));
        spans.set(SurfacePointEvaluator.spanEvaluator(rule));
        return rule;
    }

    @WrapWithCondition(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;updateY(IIIIII)V"), require = 0)
    private boolean adrenaline$updateSurfaceContext(@Coerce Object context, int above, int below, int water, int x, int y, int z,
                                                    @Share("surfaceSpans") LocalRef<SurfacePointEvaluator.Evaluator> spans) {
        SurfacePointEvaluator.Evaluator evaluator = spans.get();
        return evaluator == null || evaluator.shouldUpdate(above, below, water, x, y, z);
    }

    @ModifyVariable(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER), ordinal = 8, require = 0)
    private int adrenaline$advanceSurfaceDepth(int above, @Share("surfaceSpans") LocalRef<SurfacePointEvaluator.Evaluator> spans) {
        SurfacePointEvaluator.Evaluator evaluator = spans.get();
        return evaluator == null ? above : evaluator.spanDepth(above);
    }

    @ModifyVariable(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER), ordinal = 12, require = 0)
    private int adrenaline$advanceSurfaceY(int y, @Share("surfaceSpans") LocalRef<SurfacePointEvaluator.Evaluator> spans) {
        SurfacePointEvaluator.Evaluator evaluator = spans.get();
        return evaluator == null ? y : evaluator.spanY(y);
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
