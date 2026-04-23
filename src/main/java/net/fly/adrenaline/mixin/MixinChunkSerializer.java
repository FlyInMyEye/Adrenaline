package net.fly.adrenaline.mixin;

import com.mojang.serialization.Codec;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.ChunkSerializationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus.ChunkType;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializer {

    @Shadow
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Shadow
    private static Logger LOGGER;

    @Shadow
    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return null;
    }

    @Shadow
    private static void saveTicks(ServerLevel level, CompoundTag tag, ChunkAccess.TicksToSave ticksToSave) {
    }

    @Shadow
    public static ListTag packOffsets(it.unimi.dsi.fastutil.shorts.ShortList[] offsets) {
        return null;
    }

    @Shadow
    private static CompoundTag packStructureData(
        StructurePieceSerializationContext context,
        ChunkPos chunkPos,
        Map<Structure, StructureStart> starts,
        Map<Structure, LongSet> references
    ) {
        return null;
    }

    private static Tag[][] preEncodeSectionTags(LevelChunkSection[] sections, Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec, ChunkPos chunkPos) {
        Tag[][] sectionTags = new Tag[sections.length][2];
        List<CompletableFuture<Void>> futures = new ArrayList<>(sections.length);
        ForkJoinPool serializationPool = ChunkSerializationSupport.serializationPool();

        for (int i = 0; i < sections.length; i++) {
            final int idx = i;
            final LevelChunkSection section = sections[i];
            futures.add(CompletableFuture.runAsync(() -> {
                sectionTags[idx][0] = BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, section.getStates()).resultOrPartial(e -> {}).orElse(null);
                sectionTags[idx][1] = biomeCodec.encodeStart(NbtOps.INSTANCE, section.getBiomes()).resultOrPartial(e -> {}).orElse(null);
            }, serializationPool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            Adrenaline.LOGGER.warn(
                "[Adrenaline] Parallel pre-encode failed for chunk {}; falling back to synchronous serialization",
                chunkPos, e
            );
        }

        return sectionTags;
    }

    @Overwrite
    public static CompoundTag write(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        CompoundTag compoundTag = net.minecraft.nbt.NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.putInt("xPos", chunkPos.x);
        compoundTag.putInt("yPos", chunk.getMinSection());
        compoundTag.putInt("zPos", chunkPos.z);
        compoundTag.putLong("LastUpdate", level.getGameTime());
        compoundTag.putLong("InhabitedTime", chunk.getInhabitedTime());
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getStatus()).toString());

        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingData).resultOrPartial(LOGGER::error).ifPresent(tag -> compoundTag.put("blending_data", tag));
        }

        BelowZeroRetrogen belowZeroRetrogen = chunk.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen).resultOrPartial(LOGGER::error).ifPresent(tag -> compoundTag.put("below_zero_retrogen", tag));
        }

        UpgradeData upgradeData = chunk.getUpgradeData();
        if (!upgradeData.isEmpty()) {
            compoundTag.put("UpgradeData", upgradeData.write());
        }

        LevelChunkSection[] sections = chunk.getSections();
        ListTag listTag = new ListTag();
        LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = ChunkSerializationSupport.biomeCodec(biomeRegistry, MixinChunkSerializer::makeBiomeCodec);
        Tag[][] sectionTags = AdrenalineConfig.parallelChunkSerializationEnabled() ? preEncodeSectionTags(sections, biomeCodec, chunkPos) : null;
        boolean lightCorrect = chunk.isLightCorrect();

        for (int sectionY = lightEngine.getMinLightSection(); sectionY < lightEngine.getMaxLightSection(); sectionY++) {
            int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
            boolean validSection = sectionIndex >= 0 && sectionIndex < sections.length;
            DataLayer blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkPos, sectionY));
            DataLayer skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkPos, sectionY));
            if (validSection || blockLight != null || skyLight != null) {
                CompoundTag sectionTag = new CompoundTag();
                if (validSection) {
                    LevelChunkSection section = sections[sectionIndex];
                    sectionTag.put(
                        "block_states",
                        (Tag) ChunkSerializationSupport.cachedEncodeStart(sections, sectionTags, BLOCK_STATE_CODEC, NbtOps.INSTANCE, section.getStates()).getOrThrow(false, LOGGER::error)
                    );
                    sectionTag.put(
                        "biomes",
                        (Tag) ChunkSerializationSupport.cachedEncodeStart(sections, sectionTags, biomeCodec, NbtOps.INSTANCE, section.getBiomes()).getOrThrow(false, LOGGER::error)
                    );
                }

                if (blockLight != null && !blockLight.isEmpty()) {
                    sectionTag.putByteArray("BlockLight", blockLight.getData());
                }

                if (skyLight != null && !skyLight.isEmpty()) {
                    sectionTag.putByteArray("SkyLight", skyLight.getData());
                }

                if (!sectionTag.isEmpty()) {
                    sectionTag.putByte("Y", (byte) sectionY);
                    listTag.add(sectionTag);
                }
            }
        }

        compoundTag.put("sections", listTag);
        if (lightCorrect) {
            compoundTag.putBoolean("isLightOn", true);
        }

        ListTag blockEntities = new ListTag();
        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag blockEntityTag = chunk.getBlockEntityNbtForSaving(blockPos);
            if (blockEntityTag != null) {
                blockEntities.add(blockEntityTag);
            }
        }

        compoundTag.put("block_entities", blockEntities);
        if (chunk.getStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk) chunk;
            ListTag entities = new ListTag();
            entities.addAll(protoChunk.getEntities());
            compoundTag.put("entities", entities);
            CompoundTag carvingMasks = new CompoundTag();

            for (Carving carving : Carving.values()) {
                CarvingMask carvingMask = protoChunk.getCarvingMask(carving);
                if (carvingMask != null) {
                    carvingMasks.putLongArray(carving.toString(), carvingMask.toArray());
                }
            }

            compoundTag.put("CarvingMasks", carvingMasks);
        } else if (chunk instanceof LevelChunk levelChunk) {
            try {
                CompoundTag capTag = levelChunk.writeCapsToNBT();
                if (capTag != null) {
                    compoundTag.put("ForgeCaps", capTag);
                }
            } catch (Exception e) {
                LOGGER.error("A capability provider has thrown an exception trying to write state. It will not persist. Report this to the mod author", e);
            }
        }

        saveTicks(level, compoundTag, chunk.getTicksForSerialization());
        compoundTag.put("PostProcessing", packOffsets(chunk.getPostProcessing()));
        CompoundTag heightmaps = new CompoundTag();

        for (Entry<Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getStatus().heightmapsAfter().contains(entry.getKey())) {
                heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        compoundTag.put("Heightmaps", heightmaps);
        compoundTag.put(
            "structures",
            packStructureData(StructurePieceSerializationContext.fromLevel(level), chunkPos, chunk.getAllStarts(), chunk.getAllReferences())
        );
        return compoundTag;
    }
}
