package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class DensityMapCache implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final IdentityHashMap<DensityFunction, DensityFunction> mapped = new IdentityHashMap<>();
    private SplineVisitor splineVisitor;

    public DensityMapCache(DensityFunction.Visitor delegate) {
        this.delegate = delegate;
    }

    public DensityFunction get(DensityFunction original) {
        return this.mapped.get(original);
    }

    public void put(DensityFunction original, DensityFunction result) {
        this.mapped.put(original, result);
    }

    public SplineVisitor splineVisitor() {
        if (this.splineVisitor == null) {
            this.splineVisitor = new SplineVisitor(this);
        }
        return this.splineVisitor;
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        return this.delegate.apply(function);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return this.delegate.visitNoise(noise);
    }

    public static final class SplineVisitor implements CubicSpline.CoordinateVisitor<DensityFunctions.Spline.Coordinate> {
        private final DensityMapCache cache;
        private final IdentityHashMap<CubicSpline<?, ?>, CubicSpline<?, ?>> mapped = new IdentityHashMap<>();

        private SplineVisitor(DensityMapCache cache) {
            this.cache = cache;
        }

        @Override
        public DensityFunctions.Spline.Coordinate visit(DensityFunctions.Spline.Coordinate coordinate) {
            return coordinate.mapAll(this.cache);
        }

        @SuppressWarnings("unchecked")
        public <C, I extends ToFloatFunction<C>> CubicSpline<C, I> get(CubicSpline<C, I> original) {
            return (CubicSpline<C, I>) this.mapped.get(original);
        }

        public void put(CubicSpline<?, ?> original, CubicSpline<?, ?> result) {
            this.mapped.put(original, result);
        }
    }
}
