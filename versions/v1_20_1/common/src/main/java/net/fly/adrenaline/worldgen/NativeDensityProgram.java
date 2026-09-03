package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;

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

    private final ByteBuffer bytecode;
    private final int length;
    private final Object[] keepAlive;
    private final InterpolatorBinding[] interpolators;

    NativeDensityProgram(ByteBuffer bytecode, Object[] keepAlive, InterpolatorBinding[] interpolators) {
        this.bytecode = bytecode;
        this.length = bytecode.remaining();
        this.keepAlive = keepAlive;
        this.interpolators = interpolators;
    }

    public ByteBuffer bytecode() {
        return this.bytecode;
    }

    public int length() {
        return this.length;
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
}
