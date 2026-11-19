package net.fly.adrenaline.natives;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import net.fly.adrenaline.worldgen.AdrenalineMulOrAddAccess;
import net.fly.adrenaline.worldgen.DensityFunctionOperation;
import net.fly.adrenaline.worldgen.AdrenalineNormalNoiseNativeAccess;
import net.fly.adrenaline.worldgen.AdrenalineShiftNoiseAccess;
import net.fly.adrenaline.worldgen.AdrenalineShiftedNoiseAccess;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NativeClimateSampler {

    private static final int MAX_TRANSFORMS = 16;
    private static final int STRIDE = 40;
    private final ByteBuffer plan;
    private final PerlinNativeSampler.Data[] noiseData;
    private final Channel[] channels;
    private final double[] values;
    private final int mask;

    private NativeClimateSampler(ByteBuffer plan, PerlinNativeSampler.Data[] noiseData, Channel[] channels) {
        this.plan = plan;
        this.noiseData = noiseData;
        this.channels = channels;
        this.values = new double[channels.length];
        int mask = 0;
        for (Channel channel : channels) {
            mask |= 1 << channel.index;
        }
        this.mask = mask;
    }

    public static NativeClimateSampler create(Climate.Sampler sampler, int cacheable) {
        DensityFunction[] functions = { sampler.temperature(), sampler.humidity(), sampler.continentalness(),
            sampler.erosion(), sampler.depth(), sampler.weirdness() };
        var plan = ByteBuffer.allocateDirect(STRIDE * 8).order(ByteOrder.LITTLE_ENDIAN);
        var noiseData = new PerlinNativeSampler.Data[16];
        var channels = new ArrayList<Channel>(functions.length);
        NormalNoise shiftX = null;
        NormalNoise shiftZ = null;
        for (int i = 0; i < functions.length; i++) {
            if ((cacheable & (1 << i)) == 0) {
                continue;
            }
            Source source = source(i, functions[i]);
            if (source == null) {
                continue;
            }
            var noise = source.noise;
            NormalNoise x = shift(noise.adrenaline$getShiftX(), 1);
            NormalNoise z = shift(noise.adrenaline$getShiftZ(), 2);
            if (x == null || z == null) {
                continue;
            }
            if (channels.isEmpty()) {
                plan.clear();
                if (!put(plan, noiseData, 0, x, 0.25) || !put(plan, noiseData, 1, z, 0.25)) {
                    continue;
                }
                shiftX = x;
                shiftZ = z;
            }
            if (x == shiftX && z == shiftZ && put(plan, noiseData, channels.size() + 2,
                noise.adrenaline$getShiftedNoise().noise(), noise.adrenaline$getXzScale())) {
                channels.add(source.channel);
            }
        }
        if (channels.size() < 2) {
            return null;
        }
        return new NativeClimateSampler(plan.flip().slice(), noiseData, channels.toArray(Channel[]::new));
    }

    public int sample(int x, int z, float[] output, int base) {
        long started = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.CLIMATE);
        boolean sampled = false;
        try {
            if (!sampleBatch0(this.plan, x, z, this.values)) {
                return 0;
            }
            sampled = true;
            for (int i = 0; i < this.channels.length; i++) {
                Channel channel = this.channels[i];
                output[base + channel.index] = (float) channel.apply(this.values[i]);
            }
            return this.mask;
        } catch (LinkageError | RuntimeException exception) {
            AdrenalineNatives.disable(AdrenalineNatives.Feature.CLIMATE, exception);
            return 0;
        } finally {
            Reference.reachabilityFence(this.noiseData);
            if (sampled) {
                NativeRuntimeStats.end(NativeRuntimeStats.Stage.CLIMATE, started);
            }
        }
    }

    private static NormalNoise shift(DensityFunction function, int kind) {
        if (function instanceof DensityFunctions.MarkerOrMarked marker) {
            function = marker.wrapped();
        }
        return function instanceof AdrenalineShiftNoiseAccess shift && shift.adrenaline$getShiftKind() == kind
            ? shift.adrenaline$getOffsetNoise().noise() : null;
    }

    private static Source source(int index, DensityFunction function) {
        var arguments = new double[MAX_TRANSFORMS];
        int count = 0;
        int multiplications = 0;
        try {
            while (function instanceof AdrenalineMulOrAddAccess transform) {
                if (count == arguments.length) {
                    return null;
                }
                int operation = DensityFunctionOperation.ordinal(transform);
                if (operation != 0 && operation != 1) {
                    return null;
                }
                if (operation == 0) {
                    multiplications |= 1 << count;
                }
                arguments[count++] = transform.adrenaline$transformArgument();
                function = transform.adrenaline$transformInput();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return function instanceof AdrenalineShiftedNoiseAccess noise
            ? new Source(noise, new Channel(index, Arrays.copyOf(arguments, count), multiplications)) : null;
    }

    private record Source(AdrenalineShiftedNoiseAccess noise, Channel channel) {
    }

    private record Channel(int index, double[] arguments, int multiplications) {

        private double apply(double value) {
            for (int i = this.arguments.length - 1; i >= 0; i--) {
                double argument = this.arguments[i];
                value = (this.multiplications & (1 << i)) == 0 ? value + argument : value * argument;
            }
            return value;
        }
    }

    private static boolean put(ByteBuffer plan, PerlinNativeSampler.Data[] keepAlive, int index, NormalNoise noise, double scale) {
        if (!(noise instanceof AdrenalineNormalNoiseNativeAccess access) || !Double.isFinite(scale) || Math.abs(scale) > 4.0) {
            return false;
        }
        var first = access.adrenaline$getFirstNativeData();
        var second = access.adrenaline$getSecondNativeData();
        if (!first.available() || !second.available()) {
            return false;
        }
        keepAlive[index * 2] = first;
        keepAlive[index * 2 + 1] = second;
        plan.putLong(first.address()).putLong(second.address()).putInt(first.octaves()).putInt(second.octaves())
            .putDouble(access.adrenaline$getNativeValueFactor()).putDouble(scale);
        return true;
    }

    private static native boolean sampleBatch0(ByteBuffer plan, int x, int z, double[] output);
}
