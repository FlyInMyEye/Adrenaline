package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class NativeDensityProgram {

    private static final Object FLAT_CACHE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.flatCache(DensityFunctions.zero())).type();

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
    public static final byte COLUMN_CALL = 32;
    public static final byte COLUMN_RETURN = 33;
    public static final byte COLUMN_INPUT = 34;

    private final DensityFunction root;
    private final ByteBuffer bytecode;
    private final int length;
    private final Object[] keepAlive;
    private final InterpolatorBinding[] interpolators;
    private final InputBinding[] inputs;
    private final boolean emptyBlender;
    private ByteBuffer inputValues;
    private double[] sliceInput;
    private double[] cellInput;
    private final int columnCacheCount;

    NativeDensityProgram(DensityFunction root, ByteBuffer bytecode, Object[] keepAlive, InterpolatorBinding[] interpolators,
                         DensityFunction[] inputs, boolean emptyBlender, int columnCacheCount) {
        this.root = root;
        this.bytecode = bytecode;
        this.length = bytecode.remaining();
        this.keepAlive = keepAlive;
        this.interpolators = interpolators;
        this.inputs = new InputBinding[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            this.inputs[i] = new InputBinding(inputs[i], inputs[i] instanceof AdrenalineCachedDensityAccess ? CachedDensityOwner.get(inputs[i]) : null);
        }
        this.emptyBlender = emptyBlender;
        this.columnCacheCount = columnCacheCount;
    }

    AdrenalineNativeInterpolatorAccess firstTerrainInterpolator() {
        return this.length == 388 && this.inputs.length == 0 && this.interpolators.length > 0 && this.interpolators[0].offset == 1
            ? this.interpolators[0].source : null;
    }

    DensityFunction root() {
        return this.root;
    }

    DensityFunction terrainBeforeInput() {
        if (this.inputs.length != 1 || this.inputs[0].source instanceof AdrenalineCachedDensityAccess
            || !(this.root instanceof AdrenalineBinaryFunctionAccess binary)) {
            return null;
        }
        try {
            if (DensityFunctionOperation.ordinal(this.root) == 0) {
                if (binary.adrenaline$secondArgument() == this.inputs[0].source) {
                    return binary.adrenaline$firstArgument();
                }
                if (binary.adrenaline$firstArgument() == this.inputs[0].source) {
                    return binary.adrenaline$secondArgument();
                }
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return null;
    }

    public ByteBuffer bytecode() {
        return this.bytecode;
    }

    public int length() {
        return this.length;
    }

    public ByteBuffer inputValues() { return this.inputValues; }
    public int inputCount() { return this.inputs.length; }
    public int columnCacheCount() { return this.columnCacheCount; }

    public boolean prepareColumn(DensityFunction.FunctionContext context) {
        if (this.emptyBlender && context.getBlender() != Blender.empty() || this.interpolators.length != 0) {
            return false;
        }
        if (this.inputs.length > 0) {
            int bytes = this.inputs.length * 2 * Double.BYTES;
            if (this.inputValues == null || this.inputValues.capacity() != bytes) {
                this.inputValues = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
            }
            for (int i = 0; i < this.inputs.length; i++) {
                InputBinding input = this.inputs[i];
                if (!(input.source instanceof AdrenalineCachedDensityAccess cache) || input.owner == null) {
                    return false;
                }
                int offset = i * 2 * Double.BYTES;
                boolean available = cache.adrenaline$writeNativeColumnInput(this.inputValues, offset, input.owner, context.blockX(), context.blockZ());
                this.inputValues.putDouble(offset + Double.BYTES, available ? 1.0D : 0.0D);
            }
        }
        return true;
    }

    Preparation prepare(DensityFunction.ContextProvider provider, int count, boolean checkDryAir) {
        if (this.emptyBlender && provider.forIndex(0).getBlender() != Blender.empty()) {
            return Preparation.FAILED;
        }
        if (this.inputs.length > 0) {
            if (!(provider instanceof AdrenalineCellGridAccess grid)) {
                return Preparation.FAILED;
            }
            double[] sampled = null;
            if (checkDryAir && this.inputs.length == 1 && !(this.inputs[0].source instanceof AdrenalineCachedDensityAccess)) {
                if (this.cellInput == null || this.cellInput.length != count) {
                    this.cellInput = new double[count];
                }
                sampled = this.cellInput;
                DensityFunction source = this.inputs[0].source;
                boolean dryAir = true;
                for (int j = 0; j < count; j++) {
                    double value = source.compute(provider.forIndex(j));
                    sampled[j] = value;
                    dryAir &= value <= CellDensityBounds.INPUT_LIMIT && Double.isFinite(value);
                }
                if (dryAir) {
                    return Preparation.DRY_AIR;
                }
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
                        return Preparation.FAILED;
                    }
                } else if (sampled != null) {
                    for (int j = 0; j < count; j++) {
                        this.inputValues.putDouble(j * Double.BYTES, sampled[j]);
                    }
                } else {
                    for (int j = 0; j < count; j++) {
                        this.inputValues.putDouble(offset + j * Double.BYTES, input.source.compute(provider.forIndex(j)));
                    }
                }
            }
        }
        this.prepare();
        return Preparation.READY;
    }

    public boolean prepareSlice(DensityFunction.ContextProvider provider, int count) {
        DensityFunction.FunctionContext first = provider.forIndex(0);
        if (this.interpolators.length != 0 || this.emptyBlender && first.getBlender() != Blender.empty()) {
            return false;
        }
        int x = first.blockX();
        int z = first.blockZ();
        if (this.inputs.length > 0) {
            int bytes = Math.multiplyExact(Math.multiplyExact(this.inputs.length, count), Double.BYTES);
            if (this.inputValues == null || this.inputValues.capacity() != bytes) {
                this.inputValues = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
            }
            for (int i = 0; i < this.inputs.length; i++) {
                InputBinding input = this.inputs[i];
                int offset = i * count * Double.BYTES;
                if (input.source instanceof AdrenalineCachedDensityAccess cache && first == input.owner
                    && cache.adrenaline$writeNativeColumnInput(this.inputValues, offset, input.owner, x, z)) {
                    double value = this.inputValues.getDouble(offset);
                    for (int j = 1; j < count; j++) {
                        this.inputValues.putDouble(offset + j * Double.BYTES, value);
                    }
                } else if (input.source instanceof AdrenalineNoiseFunctionAccess || input.source instanceof AdrenalineBlendedNoiseAccess) {
                    if (this.sliceInput == null || this.sliceInput.length != count) {
                        this.sliceInput = new double[count];
                    }
                    input.source.fillArray(this.sliceInput, provider);
                    for (int j = 0; j < count; j++) {
                        this.inputValues.putDouble(offset + j * Double.BYTES, this.sliceInput[j]);
                    }
                } else {
                    for (int j = 0; j < count; j++) {
                        this.inputValues.putDouble(offset + j * Double.BYTES, input.source.compute(provider.forIndex(j)));
                    }
                }
            }
        }
        return true;
    }

    public boolean prepareLattice(NoiseChunk owner, PerlinSectionCache sections, int x, int z, int width, int height, int step,
                                  ByteBuffer target, double[] scratch) {
        if (this.interpolators.length != 0 || this.emptyBlender && owner.getBlender() != Blender.empty()) {
            return false;
        }
        for (InputBinding input : this.inputs) {
            if (!(input.source instanceof AdrenalineNoiseFunctionAccess) && !(input.source instanceof AdrenalineBlendedNoiseAccess && input.source instanceof BlendedNoise)
                && !(input.source instanceof AdrenalineCachedDensityAccess && input.owner == owner
                    && input.source instanceof DensityFunctions.MarkerOrMarked marker && marker.type() == FLAT_CACHE)) {
                return false;
            }
        }
        int count = width * width * height;
        for (int i = 0; i < this.inputs.length; i++) {
            InputBinding input = this.inputs[i];
            int offset = i * count * Double.BYTES;
            if (input.source instanceof AdrenalineCachedDensityAccess cache) {
                for (int cx = 0; cx < width; cx++) {
                    for (int cz = 0; cz < width; cz++) {
                        int start = offset + (cx * width + cz) * height * Double.BYTES;
                        if (!cache.adrenaline$writeNativeColumnInput(target, start, owner, x + cx * step, z + cz * step)) {
                            return false;
                        }
                        double value = target.getDouble(start);
                        for (int cy = 1; cy < height; cy++) {
                            target.putDouble(start + cy * Double.BYTES, value);
                        }
                    }
                }
            } else {
                boolean filled = input.source instanceof AdrenalineNoiseFunctionAccess noise
                    ? sections.fillGrid(input.source, noise.adrenaline$getNoise(), noise.adrenaline$getXzScale(), noise.adrenaline$getYScale(), scratch)
                    : sections.fillGrid((BlendedNoise) input.source, scratch);
                if (!filled) {
                    return false;
                }
                for (int j = 0; j < count; j++) {
                    target.putDouble(offset + j * Double.BYTES, scratch[j]);
                }
            }
        }
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

    enum Preparation {
        FAILED,
        READY,
        DRY_AIR
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
