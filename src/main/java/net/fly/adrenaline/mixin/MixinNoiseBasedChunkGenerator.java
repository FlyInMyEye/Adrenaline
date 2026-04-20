package net.fly.adrenaline.mixin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import net.fly.adrenaline.worldgen.FastHeightmap;
import net.fly.adrenaline.worldgen.FastSectionAccess;
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
        if (executor == Util.backgroundExecutor()) {
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
        if (executor == Util.backgroundExecutor()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * @author Fly
     * @reason Fast worldgen terrain writes
     */
    @Overwrite
    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess chunk, int minCellY, int cellCountY) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(access -> this.createNoiseChunk(access, structureManager, blender, randomState));
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        NoiseGeneratorSettings noiseGeneratorSettings = this.settings.value();
        BlockState defaultBlock = noiseGeneratorSettings.defaultBlock();
        WorldgenHeightmapTracker heightmapTracker = new WorldgenHeightmapTracker(chunk.getMinBuildHeight());
        Set<LevelChunkSection> dirtySections = new HashSet<>();
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
                            noiseChunk.updateForX(x, xLerp[xInCell]);

                            for (int zInCell = 0; zInCell < cellWidth; zInCell++) {
                                int z = minBlockZ + cellZ * cellWidth + zInCell;
                                int localZ = z & 15;
                                noiseChunk.updateForZ(z, zLerp[zInCell]);
                                BlockState state = noiseChunkAccessor.adrenaline$getInterpolatedState();
                                if (state == null) {
                                    state = defaultBlock;
                                }

                                state = this.debugPreliminarySurfaceLevel(noiseChunk, x, y, z, state);
                                if (state == AIR || SharedConstants.debugVoidTerrain(chunkPos)) {
                                    continue;
                                }

                                FastSectionAccess.writeUnchecked(section, localX, localY, localZ, state);
                                dirtySections.add(section);
                                heightmapTracker.record(localX, y, localZ, state);

                                if (!state.getFluidState().isEmpty()) {
                                    mutableBlockPos.set(x, y, z);
                                    chunk.markPosForPostprocessing(mutableBlockPos);
                                }
                            }
                        }
                    }
                }
            }

            if (cellX + 1 < cellCountX) {
                noiseChunk.swapSlices();
            }
        }

        noiseChunk.stopInterpolation();

        for (LevelChunkSection dirtySection : dirtySections) {
            dirtySection.recalcBlockCounts();
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                FastHeightmap.setRawHeight(worldSurface, localX, localZ, heightmapTracker.worldSurface(localX, localZ));
                FastHeightmap.setRawHeight(oceanFloor, localX, localZ, heightmapTracker.oceanFloor(localX, localZ));
            }
        }

        chunk.initializeLightSources();
        return chunk;
    }
}
