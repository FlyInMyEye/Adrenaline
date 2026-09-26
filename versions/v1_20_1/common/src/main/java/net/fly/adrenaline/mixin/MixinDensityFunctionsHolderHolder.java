package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder")
public abstract class MixinDensityFunctionsHolderHolder {
    @Inject(method = "mapAll", at = @At("HEAD"), cancellable = true)
    private void adrenaline$reuseMappedDensity(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        if (visitor instanceof DensityMapCache cache) {
            DensityFunction mapped = cache.get((DensityFunction) (Object) this);
            if (mapped != null) {
                cir.setReturnValue(mapped);
            }
        }
    }

    @Inject(method = "mapAll", at = @At("RETURN"))
    private void adrenaline$rememberMappedDensity(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        if (visitor instanceof DensityMapCache cache) {
            cache.put((DensityFunction) (Object) this, cir.getReturnValue());
        }
    }
}
