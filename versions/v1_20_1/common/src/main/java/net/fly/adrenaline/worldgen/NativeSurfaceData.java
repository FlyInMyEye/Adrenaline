package net.fly.adrenaline.worldgen;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinBiomeTemperatureAccess;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinPerlinSimplexNoiseAccess;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSimplexNoiseAccess;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

final class NativeSurfaceData {

    private static final ClassValue<Field> CLIMATE = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            for (Field field : type.getDeclaredFields()) {
                var components = field.getType().getRecordComponents();
                if (components != null) {
                    for (var component : components) {
                        if (component.getType() == Biome.TemperatureModifier.class) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                }
            }
            throw new IllegalStateException("Biome climate settings");
        }
    };
    final List<Noise> noises = new ArrayList<>();
    final List<Simplex> simplexes = new ArrayList<>();
    final List<Climate> climates = new ArrayList<>();
    final List<Integer> indices = new ArrayList<>();
    final IdentityHashMap<Biome, Integer> climateIds = new IdentityHashMap<>();
    private final Map<Noise, Integer> noiseIds = new HashMap<>();
    private final Map<NoiseSample, Integer> noiseSamples = new HashMap<>();
    private final List<Integer> noiseSlots = new ArrayList<>();
    private final IdentityHashMap<PerlinSimplexNoise, Integer> simplexIds = new IdentityHashMap<>();
    private final Map<Climate, Integer> climateValues = new HashMap<>();
    private final Map<List<Integer>, Integer> tables = new HashMap<>();
    private boolean loadedClimates;
    java.lang.invoke.MethodHandle temperatureTransform;

    int noise(NormalNoise noise, double minimum, double maximum, double yScale, double xzScale) {
        if (!AdrenalineConfig.nativePerlinBatchingEnabled() || noise.getClass() != NormalNoise.class
            || SurfacePipelineCompatibility.hasForeignHooks(NormalNoise.class)
            || SurfacePipelineCompatibility.hasForeignHooks(PerlinNoise.class)
            || SurfacePipelineCompatibility.hasForeignHooks(ImprovedNoise.class)
            || !(noise instanceof AdrenalineNormalNoiseNativeAccess access)) {
            return -1;
        }
        var first = access.adrenaline$getFirstNativeData();
        var second = access.adrenaline$getSecondNativeData();
        if (!first.available() || !second.available() || first.octaves() > 32 || second.octaves() > 32) return -1;
        Noise value = new Noise(first, second, access.adrenaline$getNativeValueFactor(), minimum, maximum, yScale, xzScale);
        Integer existing = this.noiseIds.get(value);
        if (existing != null) return existing;
        if (this.noises.size() == 1024) return -1;
        int next = this.noises.size();
        this.noises.add(value);
        this.noiseIds.put(value, next);
        var sample = new NoiseSample(first, second, value.factor, yScale, xzScale);
        this.noiseSlots.add(this.noiseSamples.computeIfAbsent(sample, key -> next));
        return next;
    }

    int noiseSlot(int index) {
        return this.noiseSlots.get(index);
    }

    int table(List<Integer> values) {
        Integer existing = this.tables.get(values);
        if (existing != null) return existing;
        if (this.indices.size() + values.size() > 262144) throw new IllegalArgumentException("Surface lookup table size");
        int offset = this.indices.size();
        List<Integer> copy = List.copyOf(values);
        this.tables.put(copy, offset);
        this.indices.addAll(copy);
        return offset;
    }

    boolean climates(Registry<Biome> registry) throws ReflectiveOperationException {
        if (this.loadedClimates) return !this.climateIds.isEmpty();
        this.loadedClimates = true;
        if (SurfacePipelineCompatibility.hasForeignHooks(net.minecraft.core.Holder.class)
            || SurfaceRuleHooks.hasHolderValueHooks(net.minecraft.core.Holder.Reference.class)
            || SurfaceRuleHooks.hasHolderValueHooks(net.minecraft.core.Holder.direct(new Object()).getClass())
            || SurfaceRuleHooks.hooked(Biome.class, "adrenaline$coldEnoughToSnow(" + org.objectweb.asm.Type.getDescriptor(net.minecraft.core.BlockPos.class) + ")Z")
                && !SurfaceTemperatureTransforms.supported()) return false;
        this.temperatureTransform = SurfaceTemperatureTransforms.helper();
        int temperature = this.simplex(AdrenalineMixinBiomeTemperatureAccess.adrenaline$getTemperatureNoise());
        int frozen = this.simplex(AdrenalineMixinBiomeTemperatureAccess.adrenaline$getFrozenTemperatureNoise());
        int information = this.simplex(Biome.BIOME_INFO_NOISE);
        if (temperature < 0 || frozen < 0 || information < 0) return false;
        for (Biome biome : registry) {
            if (biome.getClass() != Biome.class) continue;
            Object settings = CLIMATE.get(biome.getClass()).get(biome);
            Biome.TemperatureModifier modifier = null;
            for (var component : settings.getClass().getRecordComponents()) {
                if (component.getType() == Biome.TemperatureModifier.class) {
                    var getter = component.getAccessor();
                    getter.setAccessible(true);
                    modifier = (Biome.TemperatureModifier) getter.invoke(settings);
                }
            }
            if (modifier != Biome.TemperatureModifier.NONE && modifier != Biome.TemperatureModifier.FROZEN
                || SurfacePipelineCompatibility.hasForeignHooks(Biome.TemperatureModifier.class)
                || SurfacePipelineCompatibility.hasForeignHooks(modifier.getClass()) && !SurfaceTemperatureTransforms.frozenSupported(modifier.getClass())) continue;
            Climate value = new Climate(biome.getBaseTemperature(), modifier == Biome.TemperatureModifier.FROZEN ? 1 : 0, temperature, frozen, information);
            Integer id = this.climateValues.get(value);
            if (id == null) {
                id = this.climates.size();
                this.climateValues.put(value, id);
                this.climates.add(value);
            }
            this.climateIds.put(biome, id);
        }
        return !this.climateIds.isEmpty();
    }

    private int simplex(PerlinSimplexNoise noise) {
        if (noise.getClass() != PerlinSimplexNoise.class || SurfacePipelineCompatibility.hasForeignHooks(PerlinSimplexNoise.class)
            || SurfacePipelineCompatibility.hasForeignHooks(SimplexNoise.class)) return -1;
        Integer existing = this.simplexIds.get(noise);
        if (existing != null) return existing;
        var access = (AdrenalineMixinPerlinSimplexNoiseAccess) noise;
        var levels = access.adrenaline$getNoiseLevels();
        if (levels.length == 0 || levels.length > 32 || this.simplexes.size() == 16) return -1;
        byte[] packed = new byte[levels.length * 264];
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null) continue;
            if (levels[i].getClass() != SimplexNoise.class) return -1;
            int[] permutation = ((AdrenalineMixinSimplexNoiseAccess) levels[i]).adrenaline$getPermutation();
            for (int j = 0; j < 256; j++) packed[i * 264 + j] = (byte) permutation[j];
            packed[i * 264 + 256] = 1;
        }
        int next = this.simplexes.size();
        this.simplexes.add(new Simplex(packed, levels.length, access.adrenaline$getHighestFreqInputFactor(), access.adrenaline$getHighestFreqValueFactor()));
        this.simplexIds.put(noise, next);
        return next;
    }

    int simplexBytes() {
        return this.simplexes.stream().mapToInt(value -> value.packed.length).sum();
    }

    void write(ByteBuffer buffer, int noiseOffset, int simplexOffset, int climateOffset, int indexOffset, int dataOffset) {
        buffer.position(noiseOffset);
        for (Noise noise : this.noises) {
            buffer.putLong(noise.first.address()).putLong(noise.second.address()).putInt(noise.first.octaves()).putInt(noise.second.octaves())
                .putDouble(noise.factor).putDouble(noise.minimum).putDouble(noise.maximum).putDouble(noise.yScale).putDouble(noise.xzScale);
        }
        buffer.position(simplexOffset);
        int offset = dataOffset;
        for (Simplex simplex : this.simplexes) {
            buffer.putInt(offset).putInt(simplex.count).putDouble(simplex.input).putDouble(simplex.value).putLong(0L);
            offset += simplex.packed.length;
        }
        buffer.position(climateOffset);
        for (Climate climate : this.climates) {
            buffer.putFloat(climate.base).putInt(climate.modifier).putInt(climate.temperature).putInt(climate.frozen).putInt(climate.information).putInt(0);
        }
        buffer.position(indexOffset);
        for (int index : this.indices) buffer.putInt(index);
        buffer.position(dataOffset);
        for (Simplex simplex : this.simplexes) buffer.put(simplex.packed);
    }

    record Noise(PerlinNativeSampler.Data first, PerlinNativeSampler.Data second, double factor, double minimum, double maximum, double yScale, double xzScale) { }
    private record NoiseSample(PerlinNativeSampler.Data first, PerlinNativeSampler.Data second, double factor, double yScale, double xzScale) { }
    record Simplex(byte[] packed, int count, double input, double value) { }
    record Climate(float base, int modifier, int temperature, int frozen, int information) { }
}
