package net.fly.adrenaline.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DensityFunctions.Spline.Coordinate.class)
public abstract class MixinDensityFunctionsSplineCoordinate {

    @Shadow @Final private Holder<DensityFunction> function;

    @Overwrite
    public DensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
        DensityFunction input = this.function.value();
        DensityFunction mappedInput = input.mapAll(visitor);
        if (mappedInput == input) {
            return (DensityFunctions.Spline.Coordinate) (Object) this;
        }
        return new DensityFunctions.Spline.Coordinate(Holder.direct(mappedInput));
    }
}
