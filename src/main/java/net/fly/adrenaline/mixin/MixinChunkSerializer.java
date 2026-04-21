package net.fly.adrenaline.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.ChunkSerializationSupport;
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

    private static final ThreadLocal<ChunkAccess> SERIALIZING_CHUNK = new ThreadLocal<>();

    @Inject(method = "write", at = @At("HEAD"))
    private static void captureAndPreEncode(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        if (!AdrenalineConfig.parallelChunkSerializationEnabled()) {
            return;
        }

        SERIALIZING_CHUNK.set(chunk);

        LevelChunkSection[] sections = chunk.getSections();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = ChunkSerializationSupport.biomeCodec(biomeRegistry, MixinChunkSerializer::makeBiomeCodec);
        Tag[][] sectionTags = new Tag[sections.length][2];
        List<CompletableFuture<Void>> futures = new ArrayList<>(sections.length);

        ForkJoinPool serializationPool = ChunkSerializationSupport.serializationPool();
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
            }, serializationPool));
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
    private static void clearCache(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
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
        if (!AdrenalineConfig.parallelChunkSerializationEnabled()) {
            return ChunkSerializationSupport.encodeStart(codec, ops, value);
        }

        ChunkAccess chunk = SERIALIZING_CHUNK.get();
        if (chunk == null) {
            return ChunkSerializationSupport.encodeStart(codec, ops, value);
        }

        return ChunkSerializationSupport.cachedEncodeStart(chunk, codec, ops, value);
    }
}
