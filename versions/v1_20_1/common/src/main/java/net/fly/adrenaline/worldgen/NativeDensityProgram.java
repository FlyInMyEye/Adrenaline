package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class NativeDensityProgram {

    public static final byte END = 0;
    public static final byte NOISE = 1;
    public static final byte CONSTANT = 2;
    public static final byte ADD = 3;
    public static final byte MULTIPLY = 4;
    public static final byte CLAMP = 5;
    public static final byte ABS = 6;
    public static final byte SQUARE = 7;
    public static final byte CUBE = 8;
    public static final byte HALF_NEGATIVE = 9;
    public static final byte QUARTER_NEGATIVE = 10;
    public static final byte SQUEEZE = 11;
    public static final byte MIN = 12;
    public static final byte MAX = 13;
    public static final byte RANGE = 14;
    public static final byte INTERPOLATOR = 15;
    public static final byte RANGE_START = 16;
    public static final byte RANGE_INSIDE_END = 17;
    public static final byte RANGE_END = 18;
    public static final byte BINARY_GUARD = 19;
    public static final byte SHORT_CIRCUIT_MULTIPLY = 20;
    public static final byte Y_GRADIENT = 21;
    public static final byte SHIFT_NOISE = 22;
    public static final byte SHIFTED_NOISE = 23;
    public static final byte WEIRD_SCALED_NOISE = 24;
    public static final byte SPLINE_START = 25;
    public static final byte SPLINE_VALUE = 26;
    public static final byte SPLINE_APPLY = 27;
    public static final byte SPLINE_END = 28;
    public static final byte INPUT = 29;
    public static final byte BLENDED_NOISE = 30;
    public static final byte RECIPROCAL = 31;

    private final ByteBuffer bytecode;
    private final int length;
    private final Object[] keepAlive;
    private final InterpolatorBinding[] interpolators;
    private final InputBinding[] inputs;
    private final boolean emptyBlender;
    private ByteBuffer inputValues;

    NativeDensityProgram(ByteBuffer bytecode, Object[] keepAlive, InterpolatorBinding[] interpolators) {
        this(bytecode, keepAlive, interpolators, new DensityFunction[0], false);
    }

    NativeDensityProgram(ByteBuffer bytecode, Object[] keepAlive, InterpolatorBinding[] interpolators, DensityFunction[] inputs, boolean emptyBlender) {
        this.bytecode = bytecode;
        this.length = bytecode.remaining();
        this.keepAlive = keepAlive;
        this.interpolators = interpolators;
        this.inputs = new InputBinding[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            this.inputs[i] = new InputBinding(inputs[i], inputs[i] instanceof AdrenalineCachedDensityAccess ? CachedDensityOwner.get(inputs[i]) : null);
        }
        this.emptyBlender = emptyBlender;
    }

    public ByteBuffer bytecode() {
        return this.bytecode;
    }

    public int length() {
        return this.length;
    }

    public ByteBuffer inputValues() { return this.inputValues; }
    public int inputCount() { return this.inputs.length; }

    public boolean prepare(DensityFunction.ContextProvider provider, int count) {
        if (this.emptyBlender && provider.forIndex(0).getBlender() != Blender.empty()) {
            return false;
        }
        if (this.inputs.length > 0) {
            if (!(provider instanceof AdrenalineCellGridAccess grid)) {
                return false;
            }
            int bytes = Math.multiplyExact(Math.multiplyExact(this.inputs.length, count), Double.BYTES);
            if (this.inputValues == null || this.inputValues.capacity() != bytes) {
                this.inputValues = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
            }
            for (int i = 0; i < this.inputs.length; i++) {
                InputBinding input = this.inputs[i];
                int offset = i * count * Double.BYTES;
                if (input.source instanceof AdrenalineCachedDensityAccess cache) {
                    if (provider != input.owner || !cache.adrenaline$writeNativeInput(this.inputValues, offset, input.owner, grid, count)) {
                        return false;
                    }
                } else {
                    for (int j = 0; j < count; j++) {
                        this.inputValues.putDouble(offset + j * Double.BYTES, input.source.compute(provider.forIndex(j)));
                    }
                }
            }
        }
        this.prepare();
        return true;
    }

    public void prepare() {
        for (InterpolatorBinding interpolator : this.interpolators) {
            int offset = interpolator.offset;
            AdrenalineNativeInterpolatorAccess source = interpolator.source;
            this.bytecode.putDouble(offset, source.adrenaline$getNoise000());
            this.bytecode.putDouble(offset + 8, source.adrenaline$getNoise001());
            this.bytecode.putDouble(offset + 16, source.adrenaline$getNoise100());
            this.bytecode.putDouble(offset + 24, source.adrenaline$getNoise101());
            this.bytecode.putDouble(offset + 32, source.adrenaline$getNoise010());
            this.bytecode.putDouble(offset + 40, source.adrenaline$getNoise011());
            this.bytecode.putDouble(offset + 48, source.adrenaline$getNoise110());
            this.bytecode.putDouble(offset + 56, source.adrenaline$getNoise111());
        }
    }

    static final class InterpolatorBinding {

        private final AdrenalineNativeInterpolatorAccess source;
        private final int offset;

        InterpolatorBinding(AdrenalineNativeInterpolatorAccess source, int offset) {
            this.source = source;
            this.offset = offset;
        }
    }

    private record InputBinding(DensityFunction source, NoiseChunk owner) { }
}
