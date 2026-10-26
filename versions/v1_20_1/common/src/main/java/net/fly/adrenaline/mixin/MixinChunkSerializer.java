package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.ChunkSerializationSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer {

    private static final ThreadLocal<Deque<SerializationContext>> adrenaline$serializationContexts = new ThreadLocal<>();

    @Shadow
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Shadow
    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return null;
    }

    @WrapMethod(method = "write")
    @ControlsOptimization(Optimization.CHUNK_SERIALIZATION)
    private static CompoundTag adrenaline$writeSections(ServerLevel level, ChunkAccess chunk, Operation<CompoundTag> original) {
        if (!AdrenalineConfig.parallelChunkSerializationEnabled()) {
            return original.call(level, chunk);
        }
        Deque<SerializationContext> stack = adrenaline$serializationContexts.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            adrenaline$serializationContexts.set(stack);
        }
        stack.push(new SerializationContext(chunk.getPos(), chunk.getSections()));
        try {
            return original.call(level, chunk);
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                adrenaline$serializationContexts.remove();
            }
        }
    }

    private static SerializationContext adrenaline$currentContext() {
        Deque<SerializationContext> stack = adrenaline$serializationContexts.get();
        return stack == null ? null : stack.peek();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;makeBiomeCodec(Lnet/minecraft/core/Registry;)Lcom/mojang/serialization/Codec;"))
    @ControlsOptimization(Optimization.BIOME_CODEC)
    private static Codec<PalettedContainerRO<Holder<Biome>>> adrenaline$cacheBiomeCodec(Registry<Biome> biomeRegistry) {
        Codec<PalettedContainerRO<Holder<Biome>>> codec = ChunkSerializationSupport.biomeCodec(biomeRegistry, MixinChunkSerializer::makeBiomeCodec);
        SerializationContext context = adrenaline$currentContext();
        if (context != null && AdrenalineConfig.parallelChunkSerializationEnabled()) {
            context.prepare(codec);
        }
        return codec;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 2, remap = false))
    private static DataResult<?> adrenaline$encodeBlockStates(Codec<?> codec, DynamicOps<?> ops, Object value) {
        SerializationContext context = adrenaline$currentContext();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 3, remap = false))
    private static DataResult<?> adrenaline$encodeBiomes(Codec<?> codec, DynamicOps<?> ops, Object value) {
        SerializationContext context = adrenaline$currentContext();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }

    private static final class SectionTask extends CompletableFuture<Void> implements Runnable {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final LevelChunkSection section;
        private final Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec;
        private final Tag[] output;

        private SectionTask(LevelChunkSection section, Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec, Tag[] output) {
            this.section = section;
            this.biomeCodec = biomeCodec;
            this.output = output;
        }

        @Override
        public void run() {
            if (!this.claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                this.output[0] = BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, this.section.getStates()).resultOrPartial(error -> {}).orElse(null);
                this.output[1] = this.biomeCodec.encodeStart(NbtOps.INSTANCE, this.section.getBiomes()).resultOrPartial(error -> {}).orElse(null);
                this.complete(null);
            } catch (Throwable failure) {
                this.completeExceptionally(failure);
            }
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
            SectionTask[] tasks = new SectionTask[this.sections.length];
            for (int i = 0; i < this.sections.length; i++) {
                tasks[i] = new SectionTask(this.sections[i], biomeCodec, prepared[i]);
            }
            boolean help = AdrenalineConfig.parallelWorldgenEnabled() && ChunkJobScheduler.get().hasSpareWorkers();
            for (SectionTask task : tasks) {
                try {
                    serializationPool.execute(task);
                } catch (RejectedExecutionException exception) {
                    help = true;
                }
            }
            if (help) {
                for (SectionTask task : tasks) {
                    task.run();
                }
            }

            try {
                CompletableFuture.allOf(tasks).join();
                this.sectionTags = prepared;
            } catch (Exception exception) {
                GlobalCommon.LOGGER.warn("[Adrenaline] Parallel pre-encode failed for chunk {}; falling back to synchronous serialization", this.chunkPos, exception);
                this.sectionTags = null;
            }
        }

        private DataResult<?> encode(Codec<?> codec, DynamicOps<?> ops, Object value) {
            return ChunkSerializationSupport.cachedEncodeStart(this.sections, this.sectionTags, codec, ops, value);
        }
    }
}
