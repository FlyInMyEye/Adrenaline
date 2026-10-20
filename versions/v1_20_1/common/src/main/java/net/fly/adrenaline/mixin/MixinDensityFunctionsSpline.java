package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.DensityMapCache;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DensityFunctions.Spline.class)
public abstract class MixinDensityFunctionsSpline {

    @Shadow @Final private CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline;

    @WrapMethod(method = "mapAll")
    @ControlsOptimization(Optimization.DENSITY_SPLINES)
    private DensityFunction adrenaline$mapAll(DensityFunction.Visitor visitor, Operation<DensityFunction> call) {
        if (!AdrenalineConfig.densitySplineOptimizationsEnabled()) {
            return call.call(visitor);
        }
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> mappedSpline = this.spline.mapAll(
            visitor instanceof DensityMapCache cache ? cache.splineVisitor() : coordinate -> coordinate.mapAll(visitor)
        );
        DensityFunction mappedFunction = mappedSpline == this.spline
            ? (DensityFunction) (Object) this
            : new DensityFunctions.Spline(mappedSpline);
        return visitor.apply(mappedFunction);
    }
}