package net.fly.adrenaline.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CubicSpline.Multipoint.class)
public abstract class MixinCubicSplineMultipoint<C, I extends ToFloatFunction<C>> {

    @Shadow @Final private I coordinate;
    @Shadow @Final private float[] locations;
    @Shadow @Final private List<CubicSpline<C, I>> values;
    @Shadow @Final private float[] derivatives;

    @Overwrite
    public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
        I mappedCoordinate = visitor.visit(this.coordinate);
        List<CubicSpline<C, I>> mappedValues = null;

        for (int index = 0; index < this.values.size(); index++) {
            CubicSpline<C, I> value = this.values.get(index);
            CubicSpline<C, I> mappedValue = value.mapAll(visitor);
            if (mappedValue != value) {
                if (mappedValues == null) {
                    mappedValues = new ArrayList<>(this.values);
                }
                mappedValues.set(index, mappedValue);
            }
        }

        if (mappedCoordinate == this.coordinate && mappedValues == null) {
            return (CubicSpline<C, I>) (Object) this;
        }

        return MixinCubicSplineMultipointInvoker.adrenaline$create(
            mappedCoordinate,
            this.locations,
            mappedValues == null ? this.values : List.copyOf(mappedValues),
            this.derivatives
        );
    }
}
