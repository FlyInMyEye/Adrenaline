package net.fly.adrenaline.worldgen;

import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NativeDensityProgramCompiler {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_OPERATORS = 256;
    private static final int PROGRAM_CAPACITY = 16 * 1024;

    private NativeDensityProgramCompiler() {
    }

    public static Result compile(DensityFunction root) {
        Builder builder = new Builder();
        try {
            if (!builder.emit(root, 0)) {
                return new Result(null, builder.rejection == null ? root.getClass().getName() : builder.rejection);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new Result(null, builder.rejection == null ? root.getClass().getName() : builder.rejection);
        }
        builder.code.put(NativeDensityProgram.END);
        builder.code.flip();
        return new Result(new NativeDensityProgram(builder.code, builder.keepAlive.toArray(), builder.interpolators.toArray(NativeDensityProgram.InterpolatorBinding[]::new)), null);
    }

    private static int operationOrdinal(Object function) throws ReflectiveOperationException {
        RecordComponent[] components = function.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException();
        }
        for (RecordComponent component : components) {
            if (component.getType().isEnum()) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                return ((Enum<?>) accessor.invoke(function)).ordinal();
            }
        }
        throw new IllegalArgumentException();
    }

    private static final class Builder {

        private final ByteBuffer code = ByteBuffer.allocateDirect(PROGRAM_CAPACITY).order(ByteOrder.LITTLE_ENDIAN);
        private final List<Object> keepAlive = new ArrayList<>();
        private final List<NativeDensityProgram.InterpolatorBinding> interpolators = new ArrayList<>();
        private int operators;
        private String rejection;

        private boolean emit(DensityFunction function, int depth) throws ReflectiveOperationException {
            if (depth > MAX_DEPTH || ++this.operators > MAX_OPERATORS) {
                this.rejection = "limit";
                return false;
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
                this.rejection = "Beardifier with structures";
                return false;
            }
            if (function instanceof AdrenalineClampFunctionAccess clamp) {
                return this.emit(clamp.adrenaline$clampInput(), depth + 1)
                    && this.putClamp(clamp.adrenaline$clampMin(), clamp.adrenaline$clampMax());
            }
            if (function instanceof AdrenalineMappedFunctionAccess mapped) {
                if (!this.emit(mapped.adrenaline$mappedInput(), depth + 1)) {
                    return false;
                }
                return this.putMapped(operationOrdinal(mapped));
            }
            if (function instanceof AdrenalineMulOrAddAccess transform) {
                if (!this.emit(transform.adrenaline$transformInput(), depth + 1)) {
                    return false;
                }
                this.code.put(NativeDensityProgram.CONSTANT).putDouble(transform.adrenaline$transformArgument());
                int operation = operationOrdinal(transform);
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
            if (function instanceof AdrenalineBinaryFunctionAccess binary && operationOrdinal(function) == 0) {
                return this.emit(binary.adrenaline$firstArgument(), depth + 1)
                    && this.emit(binary.adrenaline$secondArgument(), depth + 1)
                    && this.put(NativeDensityProgram.ADD);
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary) {
                int operation = operationOrdinal(function);
                return this.emit(binary.adrenaline$firstArgument(), depth + 1)
                    && this.emit(binary.adrenaline$secondArgument(), depth + 1)
                    && this.putBinary(operation);
            }
            if (function instanceof AdrenalineRangeChoiceAccess range) {
                return this.emit(range.adrenaline$rangeInput(), depth + 1)
                    && this.emit(range.adrenaline$whenInRange(), depth + 1)
                    && this.emit(range.adrenaline$whenOutOfRange(), depth + 1)
                    && this.putRange(range.adrenaline$minInclusive(), range.adrenaline$maxExclusive());
            }
            this.rejection = function.getClass().getName();
            return false;
        }

        private boolean emitNoise(AdrenalineNoiseFunctionAccess function) {
            NormalNoise noise = function.adrenaline$getNoise();
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
            this.code.put(NativeDensityProgram.NOISE)
                .putLong(first.address())
                .putInt(first.octaves())
                .putLong(second.address())
                .putInt(second.octaves())
                .putDouble(nativeNoise.adrenaline$getNativeValueFactor())
                .putDouble(function.adrenaline$getXzScale())
                .putDouble(function.adrenaline$getYScale());
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
                case 1 -> this.put(NativeDensityProgram.MULTIPLY);
                case 2 -> this.put(NativeDensityProgram.MIN);
                case 3 -> this.put(NativeDensityProgram.MAX);
                default -> false;
            };
        }

        private boolean putRange(double min, double max) {
            this.code.put(NativeDensityProgram.RANGE).putDouble(min).putDouble(max);
            return true;
        }

        private boolean put(byte opcode) {
            this.code.put(opcode);
            return true;
        }
    }

    public record Result(NativeDensityProgram program, String rejection) {
    }
}
