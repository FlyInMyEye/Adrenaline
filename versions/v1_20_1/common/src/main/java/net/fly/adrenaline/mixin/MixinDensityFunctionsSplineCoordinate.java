package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DensityFunctions.Spline.Coordinate.class)
public abstract class MixinDensityFunctionsSplineCoordinate {

    @Shadow @Final private Holder<DensityFunction> function;

    @WrapMethod(method = "mapAll")
    private DensityFunctions.Spline.Coordinate adrenaline$mapAll(DensityFunction.Visitor visitor, Operation<DensityFunctions.Spline.Coordinate> call) {
        if (!AdrenalineConfig.densityMappingOptimizationsEnabled()) {
            return call.call(visitor);
        }
        DensityFunction input = this.function.value();
        DensityFunction mappedInput = input.mapAll(visitor);
        if (mappedInput == input) {
            return (DensityFunctions.Spline.Coordinate) (Object) this;
        }
        return new DensityFunctions.Spline.Coordinate(Holder.direct(mappedInput));
    }
}