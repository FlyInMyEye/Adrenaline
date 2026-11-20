package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

final class CellDensityBounds {

    static final double AIR_LIMIT = -0x1p-24;
    static final double INPUT_LIMIT = 0x1p-5;
    private static final double MAX_MAGNITUDE = 0x1p100;
    private static final Object CACHE_ONCE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.cacheOnce(DensityFunctions.zero())).type();

    private CellDensityBounds() {
    }

    static Bound compile(DensityFunction density, AdrenalineMixinNoiseInterpolatorView[] interpolators) {
        Map<DensityFunction, Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < interpolators.length; i++) {
            if (interpolators[i] instanceof AdrenalineNativeInterpolatorAccess && interpolators[i] instanceof DensityFunction function) {
                indices.put(function, i);
            }
        }
        try {
            return new Compiler(indices).compile(density, 0);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }

    static int firstAirCell(Bound density, double[][][][] slices, int x, int z, int minCellY, int from, int to, double ceiling) {
        Interval interval = density.evaluate(slices, x, z, minCellY, from, to);
        if (interval.high <= ceiling) {
            return from;
        }
        if (to - from == 1) {
            return to;
        }
        int middle = (from + to) >>> 1;
        int upper = firstAirCell(density, slices, x, z, minCellY, middle, to, ceiling);
        return upper == middle ? firstAirCell(density, slices, x, z, minCellY, from, middle, ceiling) : upper;
    }

    private static Interval corners(double[][][][] slices, int x, int z, int index, int from, int to) {
        double[][] left = slices[x][index];
        double[][] right = slices[x + 1][index];
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int y = from; y <= to; y++) {
            low = Math.min(low, Math.min(Math.min(left[z][y], left[z + 1][y]), Math.min(right[z][y], right[z + 1][y])));
            high = Math.max(high, Math.max(Math.max(left[z][y], left[z + 1][y]), Math.max(right[z][y], right[z + 1][y])));
        }
        return Interval.rounded(low, high);
    }

    interface Bound {
        Interval evaluate(double[][][][] slices, int x, int z, int minCellY, int from, int to);
    }

    private record Interval(double low, double high) implements Bound {

        private static final Interval UNKNOWN = new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        private static final Interval ZERO = new Interval(0.0D, 0.0D);

        private static Interval of(double low, double high) {
            return low >= -MAX_MAGNITUDE && high <= MAX_MAGNITUDE && low <= high ? new Interval(low, high) : UNKNOWN;
        }

        private static Interval rounded(double low, double high) {
            double margin = Math.max(Math.abs(low), Math.abs(high)) * 0x1p-40 + Double.MIN_NORMAL;
            return of(low - margin, high + margin);
        }

        @Override
        public Interval evaluate(double[][][][] slices, int x, int z, int minCellY, int from, int to) {
            return this;
        }
    }

    private static final class Compiler {

        private final Map<DensityFunction, Integer> interpolators;
        private final Map<DensityFunction, Bound> compiled = new IdentityHashMap<>();
        private int remaining = 1024;

        private Compiler(Map<DensityFunction, Integer> interpolators) {
            this.interpolators = interpolators;
        }

        private Bound compile(DensityFunction density, int depth) throws ReflectiveOperationException {
            if (depth > 64 || this.remaining-- <= 0) {
                return null;
            }
            if (this.compiled.containsKey(density)) {
                return this.compiled.get(density);
            }
            Bound bound = this.compileFunction(density, depth + 1);
            this.compiled.put(density, bound);
            return bound;
        }

        private Bound compileFunction(DensityFunction density, int depth) throws ReflectiveOperationException {
            Integer interpolator = this.interpolators.get(density);
            if (interpolator != null) {
                return (slices, x, z, minCellY, from, to) -> corners(slices, x, z, interpolator, from, to);
            }
            if (density instanceof AdrenalineConstantFunctionAccess constant) {
                double value = constant.adrenaline$getConstantValue();
                return Interval.of(value, value);
            }
            if (density instanceof AdrenalineBeardifierMarkerAccess || density == DensityFunctions.blendOffset()) {
                return Interval.ZERO;
            }
            if (density instanceof AdrenalineCompiledBeardifierAccess beardifier) {
                if (density.getClass() != Beardifier.class || !AdrenalineConfig.beardifierOptimizationsEnabled()) {
                    return null;
                }
                long firstClearY = beardifier.adrenaline$firstClearY();
                if (firstClearY == Long.MIN_VALUE) {
                    return Interval.ZERO;
                }
                return (slices, x, z, minCellY, from, to) -> ((long) minCellY + from) * 8 >= firstClearY
                    ? Interval.ZERO : Interval.UNKNOWN;
            }
            if (density == DensityFunctions.blendAlpha()) {
                return Interval.of(1.0D, 1.0D);
            }
            if (density instanceof AdrenalineBlendDensityAccess blend) {
                return this.compile(blend.adrenaline$getBlendDensityInput(), depth);
            }
            if (density instanceof DensityFunctions.MarkerOrMarked marker && marker.type() == CACHE_ONCE) {
                return this.compile(marker.wrapped(), depth);
            }
            if (density instanceof AdrenalineYGradientAccess gradient) {
                double first = gradient.adrenaline$getFromValue();
                double last = gradient.adrenaline$getToValue();
                int fromY = gradient.adrenaline$getFromY();
                int toY = gradient.adrenaline$getToY();
                if (fromY >= toY || Interval.of(Math.min(first, last), Math.max(first, last)) == Interval.UNKNOWN) {
                    return null;
                }
                return (slices, x, z, minCellY, from, to) -> {
                    double a = Mth.clampedMap(((long) minCellY + from) * 8, fromY, toY, first, last);
                    double b = Mth.clampedMap(((long) minCellY + to) * 8 - 1, fromY, toY, first, last);
                    double margin = Math.max(Math.abs(first), Math.abs(last)) * 0x1p-40 + Double.MIN_NORMAL;
                    return Interval.of(Math.min(a, b) - margin, Math.max(a, b) + margin);
                };
            }
            if (density instanceof AdrenalineClampFunctionAccess clamp) {
                double low = clamp.adrenaline$clampMin();
                double high = clamp.adrenaline$clampMax();
                return low <= high ? map(this.compile(clamp.adrenaline$clampInput(), depth), value -> Mth.clamp(value, low, high)) : null;
            }
            if (density instanceof AdrenalineMappedFunctionAccess mapped) {
                Bound input = this.compile(mapped.adrenaline$mappedInput(), depth);
                return switch (DensityFunctionOperation.ordinal(mapped)) {
                    case 0 -> mapMagnitude(input, value -> value);
                    case 1 -> mapMagnitude(input, value -> value * value);
                    case 2 -> map(input, value -> value * value * value);
                    case 3 -> map(input, value -> value < 0.0D ? value * 0.5D : value);
                    case 4 -> map(input, value -> value < 0.0D ? value * 0.25D : value);
                    case 5 -> map(input, CellDensityBounds::squeeze);
                    default -> null;
                };
            }
            if (density instanceof AdrenalineMulOrAddAccess transform) {
                double argument = transform.adrenaline$transformArgument();
                Bound input = this.compile(transform.adrenaline$transformInput(), depth);
                return combine(input, Interval.of(argument, argument), DensityFunctionOperation.ordinal(transform) == 0 ? 1 : 0, 0.0D);
            }
            if (density instanceof AdrenalineBinaryFunctionAccess binary) {
                DensityFunction second = binary.adrenaline$secondArgument();
                int operation = DensityFunctionOperation.ordinal(density);
                double shortcut = operation == 2 ? second.minValue() : operation == 3 ? second.maxValue() : 0.0D;
                return combine(this.compile(binary.adrenaline$firstArgument(), depth), this.compile(second, depth), operation, shortcut);
            }
            if (density instanceof AdrenalineRangeChoiceAccess range) {
                Bound selector = this.compile(range.adrenaline$rangeInput(), depth);
                Bound inside = this.compile(range.adrenaline$whenInRange(), depth);
                Bound outside = this.compile(range.adrenaline$whenOutOfRange(), depth);
                return range(selector, inside, outside, range.adrenaline$minInclusive(), range.adrenaline$maxExclusive());
            }
            if (density instanceof DensityFunctions.Spline spline) {
                return this.spline(spline.spline(), depth);
            }
            return null;
        }

        private Bound spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
                             int depth) throws ReflectiveOperationException {
            if (depth > 64 || this.remaining-- <= 0) {
                return null;
            }
            if (spline instanceof CubicSpline.Constant<?, ?> constant) {
                return Interval.of(constant.value(), constant.value());
            }
            if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> points)) {
                return null;
            }
            Bound coordinate = this.compile(points.coordinate().function().value(), depth + 1);
            float[] locations = points.locations();
            int last = locations.length - 1;
            if (coordinate == null || last < 0 || locations.length != points.derivatives().length
                || locations.length != points.values().size()) {
                return null;
            }
            float previous = Float.NEGATIVE_INFINITY;
            for (float location : locations) {
                if (!Float.isFinite(location) || location <= previous) {
                    return null;
                }
                previous = location;
            }
            Bound below = points.derivatives()[0] == 0.0F ? this.spline(points.values().get(0), depth + 1) : null;
            Bound above = points.derivatives()[last] == 0.0F ? this.spline(points.values().get(last), depth + 1) : null;
            if (below == null && above == null) {
                return null;
            }
            return (slices, x, z, minCellY, from, to) -> {
                Interval input = coordinate.evaluate(slices, x, z, minCellY, from, to);
                if (below != null && (float) input.high < locations[0]) {
                    return below.evaluate(slices, x, z, minCellY, from, to);
                }
                if (above != null && (float) input.low >= locations[last]) {
                    return above.evaluate(slices, x, z, minCellY, from, to);
                }
                return Interval.UNKNOWN;
            };
        }
    }

    private static double squeeze(double value) {
        double clamped = Mth.clamp(value, -1.0D, 1.0D);
        return clamped / 2.0D - clamped * clamped * clamped / 24.0D;
    }

    private static Bound map(Bound input, DoubleUnaryOperator operation) {
        return unary(input, (low, high) -> operation.applyAsDouble(low), (low, high) -> operation.applyAsDouble(high));
    }

    private static Bound mapMagnitude(Bound input, DoubleUnaryOperator operation) {
        return unary(input, (low, high) -> operation.applyAsDouble(Math.max(0.0D, Math.max(low, -high))),
            (low, high) -> operation.applyAsDouble(Math.max(-low, high)));
    }

    private static Bound unary(Bound input, DoubleBinaryOperator min, DoubleBinaryOperator max) {
        if (input == null) {
            return null;
        }
        if (input instanceof Interval value) {
            return unaryInterval(value, min, max);
        }
        return (slices, x, z, minCellY, from, to) -> unaryInterval(input.evaluate(slices, x, z, minCellY, from, to), min, max);
    }

    private static Interval unaryInterval(Interval input, DoubleBinaryOperator min, DoubleBinaryOperator max) {
        return input == Interval.UNKNOWN ? Interval.UNKNOWN
            : Interval.rounded(min.applyAsDouble(input.low, input.high), max.applyAsDouble(input.low, input.high));
    }

    private static Bound combine(Bound first, Bound second, int operation, double shortcut) {
        if (first == null || second == null) {
            return null;
        }
        if (first instanceof Interval left && second instanceof Interval right) {
            return combineIntervals(left, right, operation, shortcut);
        }
        return (slices, x, z, minCellY, from, to) -> combineIntervals(
            first.evaluate(slices, x, z, minCellY, from, to), second.evaluate(slices, x, z, minCellY, from, to), operation, shortcut);
    }

    private static Interval combineIntervals(Interval a, Interval b, int operation, double shortcut) {
        if (a == Interval.UNKNOWN || b == Interval.UNKNOWN) {
            return Interval.UNKNOWN;
        }
        return switch (operation) {
            case 0 -> Interval.rounded(a.low + b.low, a.high + b.high);
            case 1 -> {
                double p = a.low * b.low;
                double q = a.low * b.high;
                double r = a.high * b.low;
                double s = a.high * b.high;
                yield Interval.rounded(Math.min(Math.min(p, q), Math.min(r, s)), Math.max(Math.max(p, q), Math.max(r, s)));
            }
            case 2 -> Interval.of(Math.min(a.low, b.low), Math.min(a.high, Math.max(b.high, shortcut)));
            case 3 -> Interval.of(Math.max(a.low, Math.min(b.low, shortcut)), Math.max(a.high, b.high));
            default -> Interval.UNKNOWN;
        };
    }

    private static Bound range(Bound selector, Bound inside, Bound outside, double low, double high) {
        if (inside == null && outside == null) {
            return null;
        }
        return (slices, x, z, minCellY, from, to) -> {
            if (selector != null) {
                Interval condition = selector.evaluate(slices, x, z, minCellY, from, to);
                if (condition.low >= low && condition.high < high) {
                    return inside == null ? Interval.UNKNOWN : inside.evaluate(slices, x, z, minCellY, from, to);
                }
                if (condition.high < low || condition.low >= high) {
                    return outside == null ? Interval.UNKNOWN : outside.evaluate(slices, x, z, minCellY, from, to);
                }
            }
            if (inside == null || outside == null) {
                return Interval.UNKNOWN;
            }
            Interval a = inside.evaluate(slices, x, z, minCellY, from, to);
            Interval b = outside.evaluate(slices, x, z, minCellY, from, to);
            return Interval.of(Math.min(a.low, b.low), Math.max(a.high, b.high));
        };
    }
}
