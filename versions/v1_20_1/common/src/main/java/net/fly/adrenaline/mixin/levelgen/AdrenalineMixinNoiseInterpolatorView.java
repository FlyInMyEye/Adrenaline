package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface AdrenalineMixinNoiseInterpolatorView {

    @Accessor("slice0")
    double[][] adrenaline$getSlice0();

    @Accessor("slice1")
    double[][] adrenaline$getSlice1();

    @Invoker("fillArray")
    void adrenaline$fillArray(double[] array, DensityFunction.ContextProvider contextProvider);

    @Invoker("selectCellYZ")
    void adrenaline$selectCellYZ(int cellY, int cellZ);

    @Invoker("updateForY")
    void adrenaline$updateForY(double yLerp);

    @Invoker("updateForX")
    void adrenaline$updateForX(double xLerp);

    @Invoker("updateForZ")
    void adrenaline$updateForZ(double zLerp);

    @Invoker("swapSlices")
    void adrenaline$swapSlices();
}
