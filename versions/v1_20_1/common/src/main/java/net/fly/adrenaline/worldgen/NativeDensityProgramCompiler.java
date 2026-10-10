package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.fly.adrenaline.natives.BlendedNativeSampler;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NativeDensityProgramCompiler {

    private static final int MAX_DEPTH = 128;
    private static final int MAX_OPERATORS = 8192;
    private static final int MAX_STACK = 64;
    private static final int MAX_INPUTS = 128;
    private static final int PROGRAM_CAPACITY = 256 * 1024;
    private static final Class<?> MARKER = DensityFunctions.cacheOnce(DensityFunctions.zero()).getClass();
    private static final Object CACHE_2D = ((DensityFunctions.MarkerOrMarked) DensityFunctions.cache2d(DensityFunctions.zero())).type();
    private static final Object FLAT_CACHE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.flatCache(DensityFunctions.zero())).type();
    private static final Class<?> END_ISLANDS = DensityFunctions.endIslands(0L).getClass();

    private NativeDensityProgramCompiler() {
    }

    public static Result compile(DensityFunction root) {
        Builder builder = new Builder();
        try {
            if (!builder.emit(root, 0)) {
                return new Result(null, builder.rejection == null ? root.getClass().getName() : builder.rejection);
            }
            builder.code.put(NativeDensityProgram.END);
            return new Result(new NativeDensityProgram(builder.code.finish(), builder.keepAlive.toArray(), builder.interpolators.toArray(NativeDensityProgram.InterpolatorBinding[]::new), builder.inputs.toArray(DensityFunction[]::new), builder.emptyBlender), null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new Result(null, builder.rejection == null ? root.getClass().getName() : builder.rejection);
        }
    }

    private static final class Builder {

        private final Bytecode code = new Bytecode();
        private final List<Object> keepAlive = new ArrayList<>();
        private final List<NativeDensityProgram.InterpolatorBinding> interpolators = new ArrayList<>();
        private final List<DensityFunction> inputs = new ArrayList<>();
        private final Map<DensityFunction, Integer> inputIndices = new IdentityHashMap<>();
        private final Map<DensityFunction, Boolean> heightIndependent = new IdentityHashMap<>();
        private int operators;
        private int noiseInspections;
        private int stack;
        private boolean emptyBlender;
        private String rejection;

        private boolean emit(DensityFunction function, int depth) throws ReflectiveOperationException {
            if (depth > MAX_DEPTH || ++this.operators > MAX_OPERATORS) {
                this.rejection = "limit";
                return false;
            }
            int base = this.stack;
            if (!this.emitFunction(function, depth)) {
                return false;
            }
            this.stack = base + 1;
            if (this.stack > MAX_STACK) {
                this.rejection = "stack limit";
                return false;
            }
            return true;
        }

        private boolean emitFunction(DensityFunction function, int depth) throws ReflectiveOperationException {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                return this.emit(holder.function().value(), depth + 1);
            }
            if (function instanceof DensityFunctions.Spline spline) {
                return this.emitSpline(spline.spline(), depth + 1);
            }
            if (function instanceof DensityFunctions.MarkerOrMarked marker && function.getClass() == MARKER) {
                return this.emit(marker.wrapped(), depth + 1);
            }
            if (function instanceof DensityFunctions.MarkerOrMarked marker && marker.type() == CACHE_2D
                && this.heightIndependent(marker.wrapped(), 0)) {
                return this.emit(marker.wrapped(), depth + 1);
            }
            if (function instanceof AdrenalineCachedDensityAccess) {
                return this.emitInput(function);
            }
            if (function.getClass() == END_ISLANDS) {
                return this.emitInput(function);
            }
            if (function instanceof AdrenalineReciprocalFunctionAccess reciprocal) {
                return this.emit(reciprocal.adrenaline$getReciprocalInput(), depth + 1) && this.put(NativeDensityProgram.RECIPROCAL);
            }
            if (function instanceof AdrenalineBlendedNoiseAccess blended) {
                BlendedNativeSampler.Data data = blended.adrenaline$getNativeData();
                if (!data.available()) {
                    this.rejection = "Blended noise data";
                    return false;
                }
                this.keepAlive.add(data);
                this.code.put(NativeDensityProgram.BLENDED_NOISE)
                    .putLong(data.min().address()).putLong(data.max().address()).putLong(data.main().address())
                    .putDouble(data.xzMultiplier()).putDouble(data.yMultiplier()).putDouble(data.xzFactor())
                    .putDouble(data.yFactor()).putDouble(data.smearScaleMultiplier());
                return true;
            }
            if (function instanceof AdrenalineBlendDensityAccess blend) {
                this.emptyBlender = true;
                return this.emit(blend.adrenaline$getBlendDensityInput(), depth + 1);
            }
            if (function == DensityFunctions.blendAlpha()) {
                this.emptyBlender = true;
                return this.putConstant(1.0D);
            }
            if (function == DensityFunctions.blendOffset()) {
                this.emptyBlender = true;
                return this.putConstant(0.0D);
            }
            if (function instanceof AdrenalineYGradientAccess gradient) {
                this.code.put(NativeDensityProgram.Y_GRADIENT)
                    .putInt(gradient.adrenaline$getFromY()).putInt(gradient.adrenaline$getToY())
                    .putDouble(gradient.adrenaline$getFromValue()).putDouble(gradient.adrenaline$getToValue());
                return true;
            }
            if (function instanceof AdrenalineShiftNoiseAccess shift) {
                this.code.put(NativeDensityProgram.SHIFT_NOISE).put((byte) shift.adrenaline$getShiftKind());
                return this.emitNoiseData(shift.adrenaline$getOffsetNoise().noise());
            }
            if (function instanceof AdrenalineShiftedNoiseAccess shifted) {
                if (!this.emit(shifted.adrenaline$getShiftX(), depth + 1)
                    || !this.emit(shifted.adrenaline$getShiftY(), depth + 1)
                    || !this.emit(shifted.adrenaline$getShiftZ(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.SHIFTED_NOISE).put((byte) (this.heightIndependent(function, 0) ? 1 : 0));
                return this.emitNoiseData(shifted.adrenaline$getShiftedNoise().noise())
                    && this.putNoiseScales(shifted.adrenaline$getXzScale(), shifted.adrenaline$getYScale());
            }
            if (function instanceof AdrenalineWeirdScaledNoiseAccess weird) {
                if (!this.emit(weird.adrenaline$getRarityInput(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.WEIRD_SCALED_NOISE).put((byte) DensityFunctionOperation.ordinal(function));
                return this.emitNoiseData(weird.adrenaline$getRarityNoise().noise());
            }
            if (function instanceof AdrenalineNoiseFunctionAccess noise) {
                return this.emitNoise(noise);
            }
            if (function instanceof AdrenalineNativeInterpolatorAccess interpolator) {
                return this.emitInterpolator(interpolator);
            }
            if (function instanceof AdrenalineConstantFunctionAccess constant) {
                this.code.put(NativeDensityProgram.CONSTANT).putDouble(constant.adrenaline$getConstantValue());
                return true;
            }
            if (function instanceof AdrenalineBeardifierMarkerAccess) {
                return this.putConstant(0.0D);
            }
            if (function instanceof AdrenalineEmptyBeardifierAccess beardifier) {
                if (beardifier.adrenaline$isEmpty()) {
                    return this.putConstant(0.0D);
                }
                return this.emitInput(function);
            }
            if (function instanceof AdrenalineClampFunctionAccess clamp) {
                return this.emit(clamp.adrenaline$clampInput(), depth + 1)
                    && this.putClamp(clamp.adrenaline$clampMin(), clamp.adrenaline$clampMax());
            }
            if (function instanceof AdrenalineMappedFunctionAccess mapped) {
                if (!this.emit(mapped.adrenaline$mappedInput(), depth + 1)) {
                    return false;
                }
                return this.putMapped(DensityFunctionOperation.ordinal(mapped));
            }
            if (function instanceof AdrenalineMulOrAddAccess transform) {
                if (!this.emit(transform.adrenaline$transformInput(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.CONSTANT).putDouble(transform.adrenaline$transformArgument());
                int operation = DensityFunctionOperation.ordinal(transform);
                if (operation == 0) {
                    this.code.put(NativeDensityProgram.MULTIPLY);
                    return true;
                }
                if (operation == 1) {
                    this.code.put(NativeDensityProgram.ADD);
                    return true;
                }
                return false;
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary && DensityFunctionOperation.ordinal(function) == 0) {
                return this.emit(binary.adrenaline$firstArgument(), depth + 1)
                    && this.emit(binary.adrenaline$secondArgument(), depth + 1)
                    && this.put(NativeDensityProgram.ADD);
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary) {
                int operation = DensityFunctionOperation.ordinal(function);
                if (operation < 1 || operation > 3 || !this.emit(binary.adrenaline$firstArgument(), depth + 1)) {
                    return false;
                }
                DensityFunction second = binary.adrenaline$secondArgument();
                if (!containsNoise(second, 0)) {
                    return this.emit(second, depth + 1) && this.putBinary(operation);
                }
                double bound = operation == 2 ? second.minValue() : second.maxValue();
                this.code.put(NativeDensityProgram.BINARY_GUARD).put((byte) operation).putDouble(bound);
                int jump = this.code.position();
                this.code.putInt(0);
                if (!this.emit(second, depth + 1) || !this.putBinary(operation)) {
                    return false;
                }
                this.code.putInt(jump, this.code.position());
                return true;
            }
            if (function instanceof AdrenalineRangeChoiceAccess range) {
                if (!containsNoise(range.adrenaline$whenInRange(), 0) && !containsNoise(range.adrenaline$whenOutOfRange(), 0)) {
                    if (!this.emit(range.adrenaline$rangeInput(), depth + 1)
                        || !this.emit(range.adrenaline$whenInRange(), depth + 1)
                        || !this.emit(range.adrenaline$whenOutOfRange(), depth + 1)) {
                        return false;
                    }
                    this.code.put(NativeDensityProgram.RANGE).putDouble(range.adrenaline$minInclusive()).putDouble(range.adrenaline$maxExclusive());
                    return true;
                }
                if (!this.emit(range.adrenaline$rangeInput(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.RANGE_START)
                    .putDouble(range.adrenaline$minInclusive()).putDouble(range.adrenaline$maxExclusive());
                int outside = this.code.position();
                this.code.putInt(0);
                int end = this.code.position();
                this.code.putInt(0);
                if (!this.emit(range.adrenaline$whenInRange(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.RANGE_INSIDE_END);
                this.code.putInt(outside, this.code.position());
                if (!this.emit(range.adrenaline$whenOutOfRange(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.RANGE_END);
                this.code.putInt(end, this.code.position());
                return true;
            }
            this.rejection = function.getClass().getName();
            return false;
        }

        private boolean emitSpline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, int depth) throws ReflectiveOperationException {
            if (depth > MAX_DEPTH || ++this.operators > MAX_OPERATORS) {
                this.rejection = "spline limit";
                return false;
            }
            if (spline instanceof CubicSpline.Constant<?, ?> constant) {
                return this.putConstant(constant.value());
            }
            if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multipoint)) {
                this.rejection = spline.getClass().getName();
                return false;
            }
            if (!this.emit(multipoint.coordinate().function().value(), depth + 1)) {
                return false;
            }
            int base = this.stack - 1;
            this.stack += 3;
            if (this.stack >= MAX_STACK) {
                this.rejection = "spline stack limit";
                return false;
            }
            int count = multipoint.values().size();
            if (count == 0 || count != multipoint.locations().length || count != multipoint.derivatives().length) {
                this.rejection = "spline points";
                return false;
            }
            this.code.put(NativeDensityProgram.SPLINE_START).putInt(count);
            for (int i = 0; i < count; i++) {
                this.code.putFloat(multipoint.locations()[i]).putFloat(multipoint.derivatives()[i]);
            }
            for (int i = 0; i < count; i++) {
                this.code.put(NativeDensityProgram.SPLINE_VALUE).putInt(i);
                int jump = this.code.position();
                this.code.putInt(0);
                this.stack = base + 4;
                if (!this.emitSpline(multipoint.values().get(i), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.SPLINE_APPLY);
                this.code.putInt(jump, this.code.position());
            }
            this.code.put(NativeDensityProgram.SPLINE_END);
            this.stack = base + 1;
            return true;
        }

        private boolean emitInput(DensityFunction function) {
            Integer index = this.inputIndices.get(function);
            if (index == null) {
                if (this.inputs.size() >= MAX_INPUTS) {
                    this.rejection = "input limit";
                    return false;
                }
                index = this.inputs.size();
                this.inputs.add(function);
                this.inputIndices.put(function, index);
            }
            this.code.put(NativeDensityProgram.INPUT).putInt(index);
            return true;
        }

        private boolean heightIndependent(DensityFunction function, int depth) {
            if (depth > MAX_DEPTH) {
                return false;
            }
            Boolean known = this.heightIndependent.get(function);
            if (known != null) {
                return known;
            }
            this.heightIndependent.put(function, false);
            boolean result = this.inspectHeightIndependent(function, depth);
            this.heightIndependent.put(function, result);
            return result;
        }

        private boolean inspectHeightIndependent(DensityFunction function, int depth) {
            if (function instanceof AdrenalineConstantFunctionAccess || function instanceof AdrenalineBeardifierMarkerAccess
                || function == DensityFunctions.blendAlpha() || function == DensityFunctions.blendOffset()) {
                return true;
            }
            if (function instanceof DensityFunctions.HolderHolder holder) {
                return this.heightIndependent(holder.function().value(), depth + 1);
            }
            if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                return marker.type() == FLAT_CACHE && function instanceof AdrenalineCachedDensityAccess
                    || (function.getClass() == MARKER || marker.type() == CACHE_2D) && this.heightIndependent(marker.wrapped(), depth + 1);
            }
            if (function instanceof AdrenalineNoiseFunctionAccess noise) {
                return noise.adrenaline$getYScale() == 0.0;
            }
            if (function instanceof AdrenalineReciprocalFunctionAccess reciprocal) {
                return this.heightIndependent(reciprocal.adrenaline$getReciprocalInput(), depth + 1);
            }
            if (function instanceof AdrenalineShiftNoiseAccess shift) {
                return shift.adrenaline$getShiftKind() != 0;
            }
            if (function instanceof AdrenalineShiftedNoiseAccess shifted) {
                return shifted.adrenaline$getYScale() == 0.0 && this.heightIndependent(shifted.adrenaline$getShiftX(), depth + 1)
                    && this.heightIndependent(shifted.adrenaline$getShiftY(), depth + 1) && this.heightIndependent(shifted.adrenaline$getShiftZ(), depth + 1);
            }
            if (function instanceof AdrenalineClampFunctionAccess clamp) {
                return this.heightIndependent(clamp.adrenaline$clampInput(), depth + 1);
            }
            if (function instanceof AdrenalineMappedFunctionAccess mapped) {
                return this.heightIndependent(mapped.adrenaline$mappedInput(), depth + 1);
            }
            if (function instanceof AdrenalineMulOrAddAccess transform) {
                return this.heightIndependent(transform.adrenaline$transformInput(), depth + 1);
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary) {
                return this.heightIndependent(binary.adrenaline$firstArgument(), depth + 1) && this.heightIndependent(binary.adrenaline$secondArgument(), depth + 1);
            }
            if (function instanceof AdrenalineRangeChoiceAccess range) {
                return this.heightIndependent(range.adrenaline$rangeInput(), depth + 1) && this.heightIndependent(range.adrenaline$whenInRange(), depth + 1)
                    && this.heightIndependent(range.adrenaline$whenOutOfRange(), depth + 1);
            }
            if (function instanceof DensityFunctions.Spline spline) {
                return this.heightIndependentSpline(spline.spline(), depth + 1);
            }
            return false;
        }

        private boolean heightIndependentSpline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, int depth) {
            if (depth > MAX_DEPTH) {
                return false;
            }
            if (spline instanceof CubicSpline.Constant<?, ?>) {
                return true;
            }
            if (spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multipoint
                && this.heightIndependent(multipoint.coordinate().function().value(), depth + 1)) {
                for (CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> value : multipoint.values()) {
                    if (!this.heightIndependentSpline(value, depth + 1)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private boolean containsNoise(DensityFunction function, int depth) {
            if (depth == 0) {
                this.noiseInspections = 0;
            }
            if (depth > MAX_DEPTH || ++this.noiseInspections > MAX_OPERATORS) {
                return true;
            }
            if (function instanceof AdrenalineNoiseFunctionAccess) {
                return true;
            }
            if (function instanceof AdrenalineNativeInterpolatorAccess || function instanceof AdrenalineConstantFunctionAccess
                || function instanceof AdrenalineCachedDensityAccess || function instanceof AdrenalineBeardifierMarkerAccess
                || function instanceof AdrenalineYGradientAccess) {
                return false;
            }
            if (function instanceof AdrenalineClampFunctionAccess clamp) {
                return this.containsNoise(clamp.adrenaline$clampInput(), depth + 1);
            }
            if (function instanceof AdrenalineMappedFunctionAccess mapped) {
                return this.containsNoise(mapped.adrenaline$mappedInput(), depth + 1);
            }
            if (function instanceof AdrenalineMulOrAddAccess transform) {
                return this.containsNoise(transform.adrenaline$transformInput(), depth + 1);
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary) {
                return this.containsNoise(binary.adrenaline$firstArgument(), depth + 1)
                    || this.containsNoise(binary.adrenaline$secondArgument(), depth + 1);
            }
            if (function instanceof AdrenalineRangeChoiceAccess range) {
                return this.containsNoise(range.adrenaline$rangeInput(), depth + 1)
                    || this.containsNoise(range.adrenaline$whenInRange(), depth + 1)
                    || this.containsNoise(range.adrenaline$whenOutOfRange(), depth + 1);
            }
            return true;
        }

        private boolean emitNoise(AdrenalineNoiseFunctionAccess function) {
            this.code.put(NativeDensityProgram.NOISE);
            return this.emitNoiseData(function.adrenaline$getNoise())
                && this.putNoiseScales(function.adrenaline$getXzScale(), function.adrenaline$getYScale());
        }

        private boolean emitNoiseData(NormalNoise noise) {
            if (!(noise instanceof AdrenalineNormalNoiseNativeAccess nativeNoise)) {
                return false;
            }
            PerlinNativeSampler.Data first = nativeNoise.adrenaline$getFirstNativeData();
            PerlinNativeSampler.Data second = nativeNoise.adrenaline$getSecondNativeData();
            if (!first.available() || !second.available()) {
                return false;
            }
            this.keepAlive.add(first);
            this.keepAlive.add(second);
            this.code.putLong(first.address())
                .putInt(first.octaves())
                .putLong(second.address())
                .putInt(second.octaves())
                .putDouble(nativeNoise.adrenaline$getNativeValueFactor());
            return true;
        }

        private boolean putNoiseScales(double xz, double y) {
            this.code.putDouble(xz).putDouble(y);
            return true;
        }

        private boolean emitInterpolator(AdrenalineNativeInterpolatorAccess interpolator) {
            this.code.put(NativeDensityProgram.INTERPOLATOR);
            int offset = this.code.position();
            this.interpolators.add(new NativeDensityProgram.InterpolatorBinding(interpolator, offset));
            this.code
                .putDouble(interpolator.adrenaline$getNoise000())
                .putDouble(interpolator.adrenaline$getNoise001())
                .putDouble(interpolator.adrenaline$getNoise100())
                .putDouble(interpolator.adrenaline$getNoise101())
                .putDouble(interpolator.adrenaline$getNoise010())
                .putDouble(interpolator.adrenaline$getNoise011())
                .putDouble(interpolator.adrenaline$getNoise110())
                .putDouble(interpolator.adrenaline$getNoise111());
            return true;
        }

        private boolean putClamp(double min, double max) {
            this.code.put(NativeDensityProgram.CLAMP).putDouble(min).putDouble(max);
            return true;
        }

        private boolean putConstant(double value) {
            this.code.put(NativeDensityProgram.CONSTANT).putDouble(value);
            return true;
        }

        private boolean putMapped(int operation) {
            return switch (operation) {
                case 0 -> this.put(NativeDensityProgram.ABS);
                case 1 -> this.put(NativeDensityProgram.SQUARE);
                case 2 -> this.put(NativeDensityProgram.CUBE);
                case 3 -> this.put(NativeDensityProgram.HALF_NEGATIVE);
                case 4 -> this.put(NativeDensityProgram.QUARTER_NEGATIVE);
                case 5 -> this.put(NativeDensityProgram.SQUEEZE);
                default -> false;
            };
        }

        private boolean putBinary(int operation) {
            return switch (operation) {
                case 0 -> this.put(NativeDensityProgram.ADD);
                case 1 -> this.put(NativeDensityProgram.SHORT_CIRCUIT_MULTIPLY);
                case 2 -> this.put(NativeDensityProgram.MIN);
                case 3 -> this.put(NativeDensityProgram.MAX);
                default -> false;
            };
        }

        private boolean put(byte opcode) {
            this.code.put(opcode);
            return true;
        }
    }

    private static final class Bytecode {
        private ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        private void reserve(int length) {
            if (this.buffer.remaining() >= length) {
                return;
            }
            int required = Math.addExact(this.buffer.position(), length);
            if (required > PROGRAM_CAPACITY) {
                throw new IllegalArgumentException("Density program size");
            }
            ByteBuffer larger = ByteBuffer.allocate(Math.min(PROGRAM_CAPACITY, Math.max(required, this.buffer.capacity() * 2))).order(ByteOrder.LITTLE_ENDIAN);
            this.buffer.flip();
            larger.put(this.buffer);
            this.buffer = larger;
        }

        private int position() { return this.buffer.position(); }
        private Bytecode put(byte value) { this.reserve(1); this.buffer.put(value); return this; }
        private Bytecode putInt(int value) { this.reserve(4); this.buffer.putInt(value); return this; }
        private void putInt(int index, int value) { this.buffer.putInt(index, value); }
        private Bytecode putLong(long value) { this.reserve(8); this.buffer.putLong(value); return this; }
        private Bytecode putDouble(double value) { this.reserve(8); this.buffer.putDouble(value); return this; }
        private Bytecode putFloat(float value) { this.reserve(4); this.buffer.putFloat(value); return this; }

        private ByteBuffer finish() {
            this.buffer.flip();
            ByteBuffer result = ByteBuffer.allocateDirect(this.buffer.remaining()).order(ByteOrder.LITTLE_ENDIAN);
            result.put(this.buffer).flip();
            return result;
        }
    }

    public record Result(NativeDensityProgram program, String rejection) {
    }
}
