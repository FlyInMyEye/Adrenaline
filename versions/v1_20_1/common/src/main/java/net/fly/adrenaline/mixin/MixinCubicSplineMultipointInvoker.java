package net.fly.adrenaline.mixin;

import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CubicSpline.Multipoint.class)
public interface MixinCubicSplineMultipointInvoker {

    @Invoker("create")
    static <C, I extends ToFloatFunction<C>> CubicSpline.Multipoint<C, I> adrenaline$create(
        I coordinate,
        float[] locations,
        List<CubicSpline<C, I>> values,
        float[] derivatives
    ) {
        throw new AssertionError();
    }
}
