package net.fly.adrenaline.mixin;

import net.fly.adrenaline.Adrenaline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializerRead {

    private static final ThreadLocal<Map<CompoundTag, Tag[]>> DECODE_CACHE =
        ThreadLocal.withInitial(IdentityHashMap::new);

    @Inject(method = "read", at = @At("HEAD"))
    private static void preDecode(
            ServerLevel level,
            PoiManager poiManager,
            ChunkPos pos,
            CompoundTag chunkNbt,
            CallbackInfoReturnable<ProtoChunk> cir) {

        ListTag sections = chunkNbt.getList("sections", 10);
        if (sections.isEmpty()) return;

        Map<CompoundTag, Tag[]> cache = DECODE_CACHE.get();
        cache.clear();

        List<CompletableFuture<Void>> futures = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag sectionTag = sections.getCompound(i);
            futures.add(CompletableFuture.runAsync(() -> {
                Tag[] decoded = new Tag[2];
                if (sectionTag.contains("block_states", 10)) {
                    decoded[0] = sectionTag.getCompound("block_states");
                }
                if (sectionTag.contains("biomes", 10)) {
                    decoded[1] = sectionTag.getCompound("biomes");
                }
                synchronized (cache) {
                    cache.put(sectionTag, decoded);
                }
            }, ForkJoinPool.commonPool()));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            Adrenaline.LOGGER.warn(
                "[Adrenaline] Parallel section pre-decode failed for chunk {}; " +
                "ChunkSerializer.read() will decode normally",
                pos, e);
            cache.clear();
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void clearDecodeCache(
            ServerLevel level, PoiManager poiManager,
            ChunkPos pos, CompoundTag chunkNbt,
            CallbackInfoReturnable<ProtoChunk> cir) {
        DECODE_CACHE.get().clear();
    }
}
