package net.fly.adrenaline.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.ChunkSerializationSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer {

    private static final ThreadLocal<Deque<SerializationContext>> adrenaline$serializationContexts = ThreadLocal.withInitial(ArrayDeque::new);

    @Shadow
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Shadow
    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return null;
    }

    @Inject(method = "write", at = @At("HEAD"))
    private static void adrenaline$beginWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        adrenaline$serializationContexts.get().push(new SerializationContext(chunk.getPos(), chunk.getSections()));
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;makeBiomeCodec(Lnet/minecraft/core/Registry;)Lcom/mojang/serialization/Codec;"))
    private static Codec<PalettedContainerRO<Holder<Biome>>> adrenaline$cacheBiomeCodec(Registry<Biome> biomeRegistry) {
        Codec<PalettedContainerRO<Holder<Biome>>> codec = ChunkSerializationSupport.biomeCodec(biomeRegistry, MixinChunkSerializer::makeBiomeCodec);
        SerializationContext context = adrenaline$serializationContexts.get().peek();
        if (context != null && AdrenalineConfig.parallelChunkSerializationEnabled()) {
            context.prepare(codec);
        }
        return codec;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 2, remap = false))
    private static DataResult<?> adrenaline$encodeBlockStates(Codec<?> codec, DynamicOps<?> ops, Object value) {
        SerializationContext context = adrenaline$serializationContexts.get().peek();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 3, remap = false))
    private static DataResult<?> adrenaline$encodeBiomes(Codec<?> codec, DynamicOps<?> ops, Object value) {
        SerializationContext context = adrenaline$serializationContexts.get().peek();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private static void adrenaline$endWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        Deque<SerializationContext> stack = adrenaline$serializationContexts.get();
        stack.pop();
        if (stack.isEmpty()) {
            adrenaline$serializationContexts.remove();
        }
    }

    private static final class SerializationContext {
        private final ChunkPos chunkPos;
        private final LevelChunkSection[] sections;
        private Tag[][] sectionTags;

        private SerializationContext(ChunkPos chunkPos, LevelChunkSection[] sections) {
            this.chunkPos = chunkPos;
            this.sections = sections;
        }

        private void prepare(Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec) {
            if (this.sectionTags != null) {
                return;
            }

            Tag[][] prepared = new Tag[this.sections.length][2];
            ForkJoinPool serializationPool = ChunkSerializationSupport.serializationPool();
            ObjectArrayList<CompletableFuture<Void>> futures = new ObjectArrayList<>(this.sections.length);
            for (int i = 0; i < this.sections.length; i++) {
                final int index = i;
                LevelChunkSection section = this.sections[i];
                futures.add(CompletableFuture.runAsync(() -> {
                    prepared[index][0] = (Tag) BLOCK_STATE_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, section.getStates()).resultOrPartial(error -> {}).orElse(null);
                    prepared[index][1] = (Tag) biomeCodec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, section.getBiomes()).resultOrPartial(error -> {}).orElse(null);
                }, serializationPool));
            }

            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                this.sectionTags = prepared;
            } catch (Exception exception) {
                Adrenaline.LOGGER.warn("[Adrenaline] Parallel pre-encode failed for chunk {}; falling back to synchronous serialization", this.chunkPos, exception);
                this.sectionTags = null;
            }
        }

        private DataResult<?> encode(Codec<?> codec, DynamicOps<?> ops, Object value) {
            return ChunkSerializationSupport.cachedEncodeStart(this.sections, this.sectionTags, codec, ops, value);
        }
    }
}
