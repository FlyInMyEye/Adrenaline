package net.fly.adrenaline.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.core.Holder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public final class ChunkSerializationContext {
    private final ChunkPos chunkPos;
    private final LevelChunkSection[] sections;
    private Tag[][] sectionTags;

    public ChunkSerializationContext(ChunkPos chunkPos, LevelChunkSection[] sections) {
        this.chunkPos = chunkPos;
        this.sections = sections;
    }

    public void prepare(Codec<PalettedContainer<BlockState>> blockStateCodec, Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec) {
        if (this.sectionTags != null) {
            return;
        }

        Tag[][] prepared = new Tag[this.sections.length][2];
        ForkJoinPool serializationPool = ChunkSerializationSupport.serializationPool();
        SectionTask[] tasks = new SectionTask[this.sections.length];
        for (int i = 0; i < this.sections.length; i++) {
            tasks[i] = new SectionTask(this.sections[i], blockStateCodec, biomeCodec, prepared[i]);
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

    public DataResult<?> encode(Codec<?> codec, DynamicOps<?> ops, Object value) {
        return ChunkSerializationSupport.cachedEncodeStart(this.sections, this.sectionTags, codec, ops, value);
    }

    private static final class SectionTask extends CompletableFuture<Void> implements Runnable {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final LevelChunkSection section;
        private final Codec<PalettedContainer<BlockState>> blockStateCodec;
        private final Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec;
        private final Tag[] output;

        private SectionTask(LevelChunkSection section, Codec<PalettedContainer<BlockState>> blockStateCodec,
                            Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec, Tag[] output) {
            this.section = section;
            this.blockStateCodec = blockStateCodec;
            this.biomeCodec = biomeCodec;
            this.output = output;
        }

        @Override
        public void run() {
            if (!this.claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                this.output[0] = this.blockStateCodec.encodeStart(NbtOps.INSTANCE, this.section.getStates()).resultOrPartial(error -> {}).orElse(null);
                this.output[1] = this.biomeCodec.encodeStart(NbtOps.INSTANCE, this.section.getBiomes()).resultOrPartial(error -> {}).orElse(null);
                this.complete(null);
            } catch (Throwable failure) {
                this.completeExceptionally(failure);
            }
        }
    }
}
