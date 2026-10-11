package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineMappedFunctionAccess;
import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Mapped")
public abstract class MixinDensityFunctionsMapped implements AdrenalineMappedFunctionAccess {

    @Shadow
    public abstract DensityFunction input();

    @Override
    public DensityFunction adrenaline$mappedInput() {
        return this.input();
    }

    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunctions$Mapped;", at = @At("HEAD"), cancellable = true)
    private void adrenaline$reuseMappedExpression(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        if (visitor instanceof DensityMapCache cache) {
            DensityFunction mapped = cache.get((DensityFunction) (Object) this);
            if (mapped != null) {
                cir.setReturnValue(mapped);
            }
        }
    }

    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunctions$Mapped;", at = @At("RETURN"))
    private void adrenaline$rememberMappedExpression(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        if (visitor instanceof DensityMapCache cache) {
            cache.put((DensityFunction) (Object) this, cir.getReturnValue());
        }
    }
}
