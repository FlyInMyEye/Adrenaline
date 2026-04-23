package net.fly.adrenaline.util;

import java.util.concurrent.ForkJoinPool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.biome.Biome;

public final class ChunkSerializationSupport {

    private static volatile Codec<PalettedContainerRO<Holder<Biome>>> cachedBiomeCodec;
    private static volatile Registry<Biome> cachedBiomeRegistry;
    private static volatile ForkJoinPool serializationPool;
    private static volatile int serializationPoolThreads = -1;

    private ChunkSerializationSupport() {
    }

    public static Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec(Registry<Biome> biomeRegistry, CodecFactory codecFactory) {
        Codec<PalettedContainerRO<Holder<Biome>>> codec = cachedBiomeCodec;
        if (cachedBiomeRegistry == biomeRegistry && codec != null) {
            return codec;
        }

        synchronized (ChunkSerializationSupport.class) {
            if (cachedBiomeRegistry != biomeRegistry || cachedBiomeCodec == null) {
                cachedBiomeCodec = codecFactory.create(biomeRegistry);
                cachedBiomeRegistry = biomeRegistry;
            }
            return cachedBiomeCodec;
        }
    }

    public static ForkJoinPool serializationPool() {
        int threads = AdrenalineConfig.resolvedWorkerThreads();
        ForkJoinPool pool = serializationPool;
        if (pool != null && serializationPoolThreads == threads) {
            return pool;
        }

        synchronized (ChunkSerializationSupport.class) {
            pool = serializationPool;
            if (pool != null && serializationPoolThreads == threads) {
                return pool;
            }

            ForkJoinPool replacement = new ForkJoinPool(threads);
            ForkJoinPool previous = serializationPool;
            serializationPool = replacement;
            serializationPoolThreads = threads;
            if (previous != null) {
                previous.shutdown();
            }
            return replacement;
        }
    }

    public static DataResult<?> encodeStart(Codec<?> codec, DynamicOps<?> ops, Object value) {
        @SuppressWarnings("unchecked")
        DataResult<?> result = ((Codec<Object>) codec).encodeStart((DynamicOps<Object>) ops, value);
        return result;
    }

    public static DataResult<?> cachedEncodeStart(LevelChunkSection[] sections, Tag[][] cache, Codec<?> codec, DynamicOps<?> ops, Object value) {
        if (cache != null) {
            for (int i = 0; i < sections.length; i++) {
                if (value == sections[i].getStates() && cache[i][0] != null) {
                    return DataResult.success(cache[i][0]);
                }
                if (value == sections[i].getBiomes() && cache[i][1] != null) {
                    return DataResult.success(cache[i][1]);
                }
            }
        }

        return encodeStart(codec, ops, value);
    }

    @FunctionalInterface
    public interface CodecFactory {
        Codec<PalettedContainerRO<Holder<Biome>>> create(Registry<Biome> biomeRegistry);
    }
}
