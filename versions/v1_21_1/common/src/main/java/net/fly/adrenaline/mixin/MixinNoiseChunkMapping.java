package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunkMapping {
    @Unique private DensityMapCache adrenaline$densityMapCache;

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

    @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
    private void adrenaline$releaseMappingCache(CallbackInfoReturnable<?> cir) {
        this.adrenaline$densityMapCache = null;
    }
}
