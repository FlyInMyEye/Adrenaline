package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public class MixinRandomStateMapping {

    @Unique
    private DensityFunction.Visitor adrenaline$originalVisitor;

    @Unique
    private DensityMapCache adrenaline$mappingCache;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"), index = 0)
    private DensityFunction.Visitor adrenaline$reuseRouterMapping(DensityFunction.Visitor visitor) {
        return this.adrenaline$mappingVisitor(visitor);
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"), index = 0)
    private DensityFunction.Visitor adrenaline$reuseClimateMapping(DensityFunction.Visitor visitor) {
        return this.adrenaline$mappingVisitor(visitor);
    }

    @Unique
    private DensityFunction.Visitor adrenaline$mappingVisitor(DensityFunction.Visitor visitor) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return visitor;
        }
        if (this.adrenaline$originalVisitor != visitor) {
            this.adrenaline$originalVisitor = visitor;
            this.adrenaline$mappingCache = new DensityMapCache(visitor);
        }
        return this.adrenaline$mappingCache;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$releaseMappingCache(CallbackInfo ci) {
        this.adrenaline$originalVisitor = null;
        this.adrenaline$mappingCache = null;
    }
}
