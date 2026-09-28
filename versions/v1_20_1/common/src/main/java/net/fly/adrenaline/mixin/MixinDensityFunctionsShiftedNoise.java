package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineConstantFunctionAccess;
import net.fly.adrenaline.worldgen.AdrenalineShiftedNoiseAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public abstract class MixinDensityFunctionsShiftedNoise implements AdrenalineShiftedNoiseAccess {
    @Unique private static final Object adrenaline$flatCacheType = ((DensityFunctions.MarkerOrMarked) DensityFunctions.flatCache(DensityFunctions.zero())).type();

    @Shadow @Final private DensityFunction shiftX;
    @Shadow @Final private DensityFunction shiftY;
    @Shadow @Final private DensityFunction shiftZ;
    @Shadow @Final private double yScale;

    @Override
    public boolean adrenaline$isHeightIndependent() {
        return Double.doubleToRawLongBits(this.yScale) == 0L
            && this.shiftY instanceof AdrenalineConstantFunctionAccess constant
            && Double.doubleToRawLongBits(constant.adrenaline$getConstantValue()) == 0L
            && this.shiftX instanceof DensityFunctions.MarkerOrMarked x
            && x.type() == adrenaline$flatCacheType
            && this.shiftZ instanceof DensityFunctions.MarkerOrMarked z
            && z.type() == adrenaline$flatCacheType;
    }
}
