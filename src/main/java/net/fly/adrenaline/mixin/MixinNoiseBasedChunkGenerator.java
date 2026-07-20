package net.fly.adrenaline.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseProfile;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseSubstage;
import net.fly.adrenaline.worldgen.FastHeightmap;
import net.fly.adrenaline.worldgen.NoiseSectionWriter;
import net.fly.adrenaline.worldgen.WorldgenHeightmapTracker;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {

    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @Shadow private static BlockState AIR;

    @Shadow
    private BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int x, int y, int z, BlockState state) {
        return state;
    }

    @Shadow
    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState randomState) {
        return null;
    }

    @Redirect(
        method = "createBiomes",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> inlineCreateBiomes(Supplier<T> supplier, Executor executor) {
        if (AdrenalineConfig.terrainFillOptimizationsEnabled() && executor == Util.backgroundExecutor()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> inlineFillFromNoise(Supplier<T> supplier, Executor executor) {
        if (AdrenalineConfig.terrainFillOptimizationsEnabled() && executor == Util.backgroundExecutor()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> inlineFillFromNoiseCompletion(CompletableFuture<T> future, BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        if (AdrenalineConfig.terrainFillOptimizationsEnabled()) {
            return future.whenComplete(action);
        }
        return future.whenCompleteAsync(action, executor);
    }

    /**
     * @author Fly
     * @reason Fast worldgen terrain writes
     */
    @Overwrite
    public ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess chunk, int minCellY, int cellCountY) {
        if (!AdrenalineConfig.terrainFillOptimizationsEnabled()) {
            return this.doFillFallback(blender, structureManager, randomState, chunk, minCellY, cellCountY);
        }

        NoiseProfile profile = WorldgenStageStats.beginNoiseProfile();
        long phaseStart = profile == null ? 0L : System.nanoTime();
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(access -> this.createNoiseChunk(access, structureManager, blender, randomState));
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        NoiseGeneratorSettings noiseGeneratorSettings = this.settings.value();
        BlockState defaultBlock = noiseGeneratorSettings.defaultBlock();
        Aquifer aquifer = noiseChunk.aquifer();
        WorldgenHeightmapTracker heightmapTracker = new WorldgenHeightmapTracker(chunk.getMinBuildHeight());
        NoiseSectionWriter[] sectionWriters = new NoiseSectionWriter[chunk.getSectionsCount()];
        MutableBlockPos mutableBlockPos = new MutableBlockPos();
        MixinNoiseChunkAccessor noiseChunkAccessor = (MixinNoiseChunkAccessor) noiseChunk;
        int cellWidth = noiseChunkAccessor.adrenaline$cellWidth();
        int cellHeight = noiseChunkAccessor.adrenaline$cellHeight();
        int cellCountX = 16 / cellWidth;
        int cellCountZ = 16 / cellWidth;
        double[] xLerp = new double[cellWidth];
        double[] zLerp = new double[cellWidth];

        for (int i = 0; i < cellWidth; i++) {
            double lerp = (double) i / (double) cellWidth;
            xLerp[i] = lerp;
            zLerp[i] = lerp;
        }
        if (profile != null) {
            profile.add(NoiseSubstage.SETUP, System.nanoTime() - phaseStart);
            phaseStart = System.nanoTime();
        }

        noiseChunk.initializeForFirstCellX();
        if (profile != null) {
            profile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - phaseStart);
        }

        for (int cellX = 0; cellX < cellCountX; cellX++) {
            if (profile != null) {
                phaseStart = System.nanoTime();
            }
            noiseChunk.advanceCellX(cellX);
            if (profile != null) {
                profile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - phaseStart);
            }

            for (int cellZ = 0; cellZ < cellCountZ; cellZ++) {
                int sectionIndex = chunk.getSectionsCount() - 1;
                LevelChunkSection section = chunk.getSection(sectionIndex);
                NoiseSectionWriter sectionWriter = sectionWriters[sectionIndex];
                if (sectionWriter == null) {
                    sectionWriter = new NoiseSectionWriter(section);
                    sectionWriters[sectionIndex] = sectionWriter;
                }

                for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                    if (profile != null) {
                        phaseStart = System.nanoTime();
                    }
                    noiseChunk.selectCellYZ(cellY, cellZ);
                    if (profile != null) {
                        profile.add(NoiseSubstage.CELL_CACHE, System.nanoTime() - phaseStart);
                    }

                    for (int yInCell = cellHeight - 1; yInCell >= 0; yInCell--) {
                        int y = (minCellY + cellY) * cellHeight + yInCell;
                        int localY = y & 15;
                        int targetSectionIndex = chunk.getSectionIndex(y);

                        if (sectionIndex != targetSectionIndex) {
                            sectionIndex = targetSectionIndex;
                            section = chunk.getSection(sectionIndex);
                            sectionWriter = sectionWriters[sectionIndex];
                            if (sectionWriter == null) {
                                sectionWriter = new NoiseSectionWriter(section);
                                sectionWriters[sectionIndex] = sectionWriter;
                            }
                        }

                        if (profile != null) {
                            phaseStart = System.nanoTime();
                        }
                        noiseChunk.updateForY(y, (double) yInCell / (double) cellHeight);
                        if (profile != null) {
                            profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                        }

                        for (int xInCell = 0; xInCell < cellWidth; xInCell++) {
                            int x = minBlockX + cellX * cellWidth + xInCell;
                            int localX = x & 15;
                            if (profile != null) {
                                phaseStart = System.nanoTime();
                            }
                            noiseChunk.updateForX(x, xLerp[xInCell]);
                            if (profile != null) {
                                profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                            }

                            for (int zInCell = 0; zInCell < cellWidth; zInCell++) {
                                int z = minBlockZ + cellZ * cellWidth + zInCell;
                                int localZ = z & 15;
                                if (profile != null) {
                                    phaseStart = System.nanoTime();
                                }
                                noiseChunk.updateForZ(z, zLerp[zInCell]);
                                if (profile != null) {
                                    profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                                    phaseStart = System.nanoTime();
                                }
                                BlockState state = noiseChunkAccessor.adrenaline$getInterpolatedState();
                                if (state == null) {
                                    state = defaultBlock;
                                }

                                state = this.debugPreliminarySurfaceLevel(noiseChunk, x, y, z, state);
                                if (profile != null) {
                                    profile.add(NoiseSubstage.BLOCK_STATE, System.nanoTime() - phaseStart);
                                    phaseStart = System.nanoTime();
                                }
                                if (state == AIR || SharedConstants.debugVoidTerrain(chunkPos)) {
                                    if (profile != null) {
                                        profile.add(NoiseSubstage.BLOCK_WRITE, System.nanoTime() - phaseStart);
                                    }
                                    continue;
                                }

                                sectionWriter.set(localX, localY, localZ, state);
                                heightmapTracker.record(localX, y, localZ, state);

                                if (aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                                    mutableBlockPos.set(x, y, z);
                                    chunk.markPosForPostprocessing(mutableBlockPos);
                                }
                                if (profile != null) {
                                    profile.add(NoiseSubstage.BLOCK_WRITE, System.nanoTime() - phaseStart);
                                }
                            }
                        }
                    }
                }
            }

            if (cellX + 1 < cellCountX) {
                if (profile != null) {
                    phaseStart = System.nanoTime();
                }
                noiseChunk.swapSlices();
                if (profile != null) {
                    profile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - phaseStart);
                }
            }
        }

        if (profile != null) {
            phaseStart = System.nanoTime();
        }
        noiseChunk.stopInterpolation();

        for (NoiseSectionWriter sectionWriter : sectionWriters) {
            if (sectionWriter != null) {
                sectionWriter.finish();
            }
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                FastHeightmap.setRawHeight(worldSurface, localX, localZ, heightmapTracker.worldSurface(localX, localZ));
                FastHeightmap.setRawHeight(oceanFloor, localX, localZ, heightmapTracker.oceanFloor(localX, localZ));
            }
        }

        chunk.initializeLightSources();
        if (profile != null) {
            profile.add(NoiseSubstage.FINALIZE, System.nanoTime() - phaseStart);
            WorldgenStageStats.finishNoiseProfile(profile);
        }
        return chunk;
    }

    private ChunkAccess doFillFallback(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess chunk, int minCellY, int cellCountY) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(access -> this.createNoiseChunk(access, structureManager, blender, randomState));
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        Aquifer aquifer = noiseChunk.aquifer();
        int cellWidth = ((MixinNoiseChunkAccessor) noiseChunk).adrenaline$cellWidth();
        int cellHeight = ((MixinNoiseChunkAccessor) noiseChunk).adrenaline$cellHeight();
        int cellCountX = 16 / cellWidth;
        int cellCountZ = 16 / cellWidth;
        MutableBlockPos mutableBlockPos = new MutableBlockPos();

        noiseChunk.initializeForFirstCellX();

        for (int cellX = 0; cellX < cellCountX; cellX++) {
            noiseChunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < cellCountZ; cellZ++) {
                int sectionIndex = chunk.getSectionsCount() - 1;
                LevelChunkSection section = chunk.getSection(sectionIndex);
                for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                    noiseChunk.selectCellYZ(cellY, cellZ);
                    for (int yInCell = cellHeight - 1; yInCell >= 0; yInCell--) {
                        int y = (minCellY + cellY) * cellHeight + yInCell;
                        int localY = y & 15;
                        int targetSectionIndex = chunk.getSectionIndex(y);

                        if (sectionIndex != targetSectionIndex) {
                            sectionIndex = targetSectionIndex;
                            section = chunk.getSection(sectionIndex);
                        }

                        noiseChunk.updateForY(y, (double) yInCell / (double) cellHeight);
                        for (int xInCell = 0; xInCell < cellWidth; xInCell++) {
                            int x = minBlockX + cellX * cellWidth + xInCell;
                            int localX = x & 15;
                            noiseChunk.updateForX(x, (double) xInCell / (double) cellWidth);
                            for (int zInCell = 0; zInCell < cellWidth; zInCell++) {
                                int z = minBlockZ + cellZ * cellWidth + zInCell;
                                int localZ = z & 15;
                                noiseChunk.updateForZ(z, (double) zInCell / (double) cellWidth);
                                BlockState state = ((MixinNoiseChunkAccessor) noiseChunk).adrenaline$getInterpolatedState();
                                if (state == null) {
                                    state = this.settings.value().defaultBlock();
                                }
                                state = this.debugPreliminarySurfaceLevel(noiseChunk, x, y, z, state);
                                if (state == AIR || SharedConstants.debugVoidTerrain(chunkPos)) {
                                    continue;
                                }
                                section.setBlockState(localX, localY, localZ, state, false);
                                oceanFloor.update(localX, y, localZ, state);
                                worldSurface.update(localX, y, localZ, state);
                                if (aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                                    mutableBlockPos.set(x, y, z);
                                    chunk.markPosForPostprocessing(mutableBlockPos);
                                }
                            }
                        }
                    }
                }
            }
            noiseChunk.swapSlices();
        }

        noiseChunk.stopInterpolation();
        return chunk;
    }
}
