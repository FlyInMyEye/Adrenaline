package net.fly.adrenaline.mixin;

import java.util.List;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineShiftedNoiseAccess;
import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunkMapping {
    @Unique private DensityMapCache adrenaline$densityMapCache;

    @Shadow
    protected abstract DensityFunction wrap(DensityFunction function);

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    @ControlsOptimization(value = Optimization.DENSITY_MAPPING, local = true)
    private DensityFunction.Visitor adrenaline$cacheRouterMapping(DensityFunction.Visitor visitor) {
        return this.adrenaline$mappingVisitor(visitor);
    }

    @ModifyArg(method = {"<init>", "cachedClimateSampler"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    @ControlsOptimization(value = Optimization.DENSITY_MAPPING, local = true)
    private DensityFunction.Visitor adrenaline$cacheDensityMapping(DensityFunction.Visitor visitor) {
        return this.adrenaline$mappingVisitor(visitor);
    }

    @Unique
    private DensityFunction.Visitor adrenaline$mappingVisitor(DensityFunction.Visitor visitor) {
        if (!AdrenalineConfig.densityMappingOptimizationsEnabled()) {
            return visitor;
        }
        if (this.adrenaline$densityMapCache == null) {
            this.adrenaline$densityMapCache = new DensityMapCache(visitor);
        }
        return this.adrenaline$densityMapCache;
    }

    @Inject(method = "cachedClimateSampler", at = @At("RETURN"), cancellable = true)
    @ControlsOptimization(value = Optimization.BIOME_CLIMATE, local = true)
    private void adrenaline$cacheClimateColumns(NoiseRouter router, List<Climate.ParameterPoint> spawnTarget, CallbackInfoReturnable<Climate.Sampler> cir) {
        this.adrenaline$densityMapCache = null;
        if (!AdrenalineConfig.biomeClimateOptimizationsEnabled()) {
            return;
        }
        Climate.Sampler sampler = cir.getReturnValue();
        DensityFunction temperature = this.adrenaline$cacheClimateNoise(sampler.temperature());
        DensityFunction humidity = this.adrenaline$cacheClimateNoise(sampler.humidity());
        if (temperature != sampler.temperature() || humidity != sampler.humidity()) {
            cir.setReturnValue(new Climate.Sampler(temperature, humidity, sampler.continentalness(), sampler.erosion(),
                sampler.depth(), sampler.weirdness(), sampler.spawnTarget()));
        }
    }

    @Unique
    private DensityFunction adrenaline$cacheClimateNoise(DensityFunction function) {
        if (function instanceof AdrenalineShiftedNoiseAccess shifted && shifted.adrenaline$isHeightIndependent()) {
            return this.wrap(DensityFunctions.flatCache(function));
        }
        return function;
    }
}
