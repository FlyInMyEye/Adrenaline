package net.fly.adrenaline.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.SectionSerializationCache;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializer {

    @Shadow
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Shadow
    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return null;
    }

    private static volatile Codec<PalettedContainerRO<Holder<Biome>>> CACHED_BIOME_CODEC;
    private static volatile Registry<Biome> CACHED_BIOME_REGISTRY;

    private static final ThreadLocal<ChunkAccess> SERIALIZING_CHUNK = new ThreadLocal<>();

    @Inject(method = "write", at = @At("HEAD"))
    private static void captureAndPreEncode(
            ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> cir) {
        SERIALIZING_CHUNK.set(chunk);

        LevelChunkSection[] sections = chunk.getSections();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        if (CACHED_BIOME_REGISTRY != biomeRegistry) {
            CACHED_BIOME_CODEC = makeBiomeCodec(biomeRegistry);
            CACHED_BIOME_REGISTRY = biomeRegistry;
        }
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = CACHED_BIOME_CODEC;
        Tag[][] sectionTags = new Tag[sections.length][2];
        List<CompletableFuture<Void>> futures = new ArrayList<>(sections.length);

        for (int i = 0; i < sections.length; i++) {
            final int idx = i;
            final LevelChunkSection section = sections[i];
            futures.add(CompletableFuture.runAsync(() -> {
                sectionTags[idx][0] = BLOCK_STATE_CODEC
                        .encodeStart(NbtOps.INSTANCE, section.getStates())
                        .resultOrPartial(e -> {}).orElse(null);
                sectionTags[idx][1] = biomeCodec
                        .encodeStart(NbtOps.INSTANCE, section.getBiomes())
                        .resultOrPartial(e -> {}).orElse(null);
            }, ForkJoinPool.commonPool()));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            if (chunk instanceof SectionSerializationCache ssc) {
                ssc.adrenaline$setSectionTags(sectionTags);
            }
        } catch (Exception e) {
            Adrenaline.LOGGER.warn(
                "[Adrenaline] Parallel pre-encode failed for chunk {}; falling back to synchronous serialization",
                chunk.getPos(), e);
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private static void clearCache(
            ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> cir) {
        if (chunk instanceof SectionSerializationCache ssc) {
            ssc.adrenaline$setSectionTags(null);
        }
        SERIALIZING_CHUNK.remove();
    }

    @Redirect(
        method = "write",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
            remap = false
        ),
        remap = false
    )
    private static DataResult<?> useCachedEncoding(Codec<?> codec, DynamicOps<?> ops, Object value) {
        ChunkAccess chunk = SERIALIZING_CHUNK.get();
        if (chunk instanceof SectionSerializationCache ssc) {
            Tag[][] cache = ssc.adrenaline$getSectionTags();
            if (cache != null) {
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (value == sections[i].getStates() && cache[i][0] != null) {
                        return DataResult.success(cache[i][0]);
                    }
                    if (value == sections[i].getBiomes() && cache[i][1] != null) {
                        return DataResult.success(cache[i][1]);
                    }
                }
            }
        }
        @SuppressWarnings("unchecked")
        DataResult<?> result = ((Codec<Object>) codec).encodeStart((DynamicOps<Object>) ops, value);
        return result;
    }
}
