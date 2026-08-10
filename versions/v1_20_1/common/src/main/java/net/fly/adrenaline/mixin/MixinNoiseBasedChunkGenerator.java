package net.fly.adrenaline.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseProfile;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseSubstage;
import net.fly.adrenaline.worldgen.FastHeightmap;
import net.fly.adrenaline.worldgen.AdrenalineNoiseChunkMaterialAccess;
import net.fly.adrenaline.worldgen.AdrenalineFastAquiferAccess;
import net.fly.adrenaline.worldgen.AdrenalineNoiseChunkCoordinateAccess;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1100)
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
        if (AdrenalineConfig.inlineTerrainFillTasks() && executor == Util.backgroundExecutor()) {
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
        if (AdrenalineConfig.inlineTerrainFillTasks() && executor == Util.backgroundExecutor()) {
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
        if (AdrenalineConfig.inlineTerrainFillTasks()) {
            return future.whenComplete(action);
        }
        return future.whenCompleteAsync(action, executor);
    }

    @Inject(method = "doFill", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.TERRAIN_FILL)
    private void adrenaline$fastFill(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess chunk, int minCellY, int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        if (!AdrenalineConfig.terrainFillOptimizationsEnabled()) {
            return;
        }

        NoiseProfile profile = BuildConfig.DEBUG ? WorldgenStageStats.beginNoiseProfile() : null;
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
        AdrenalineNoiseChunkMaterialAccess materialAccess = (AdrenalineNoiseChunkMaterialAccess) noiseChunk;
        boolean directMaterialPath = materialAccess.adrenaline$hasDirectMaterialPath();
        boolean precomputedMaterialPath = directMaterialPath
            && materialAccess.adrenaline$hasPrecomputedMaterialPath()
            && aquifer instanceof AdrenalineFastAquiferAccess fastAquifer
            && fastAquifer.adrenaline$supportsPrecomputedMaterials();
        AdrenalineNoiseChunkCoordinateAccess coordinateAccess = (AdrenalineNoiseChunkCoordinateAccess) noiseChunk;
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
        if (BuildConfig.DEBUG && profile != null) {
            profile.add(NoiseSubstage.SETUP, System.nanoTime() - phaseStart);
            phaseStart = System.nanoTime();
        }

        noiseChunk.initializeForFirstCellX();
        if (BuildConfig.DEBUG && profile != null) {
            profile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - phaseStart);
        }

        for (int cellX = 0; cellX < cellCountX; cellX++) {
            if (BuildConfig.DEBUG && profile != null) {
                phaseStart = System.nanoTime();
            }
            noiseChunk.advanceCellX(cellX);
            if (BuildConfig.DEBUG && profile != null) {
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
                    if (BuildConfig.DEBUG && profile != null) {
                        phaseStart = System.nanoTime();
                    }
                    noiseChunk.selectCellYZ(cellY, cellZ);
                    double[] finalDensityValues = directMaterialPath ? materialAccess.adrenaline$finalDensityValues() : null;
                    int materialIndex = 0;
                    if (BuildConfig.DEBUG && profile != null) {
                        long elapsedNanos = System.nanoTime() - phaseStart;
                        long oreCacheNanos = materialAccess.adrenaline$consumeMaterialArrayFillNanos();
                        profile.add(NoiseSubstage.CELL_CACHE, Math.max(0L, elapsedNanos - oreCacheNanos));
                        profile.add(NoiseSubstage.ORE_CACHE, oreCacheNanos);
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

                        if (BuildConfig.DEBUG && profile != null) {
                            phaseStart = System.nanoTime();
                        }
                        if (precomputedMaterialPath) {
                            coordinateAccess.adrenaline$updateYCoordinate(y);
                        } else {
                            noiseChunk.updateForY(y, (double) yInCell / (double) cellHeight);
                        }
                        if (BuildConfig.DEBUG && profile != null) {
                            profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                        }

                        for (int xInCell = 0; xInCell < cellWidth; xInCell++) {
                            int x = minBlockX + cellX * cellWidth + xInCell;
                            int localX = x & 15;
                            if (BuildConfig.DEBUG && profile != null) {
                                phaseStart = System.nanoTime();
                            }
                            if (precomputedMaterialPath) {
                                coordinateAccess.adrenaline$updateXCoordinate(x);
                            } else {
                                noiseChunk.updateForX(x, xLerp[xInCell]);
                            }
                            if (BuildConfig.DEBUG && profile != null) {
                                profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                            }

                            for (int zInCell = 0; zInCell < cellWidth; zInCell++) {
                                int z = minBlockZ + cellZ * cellWidth + zInCell;
                                int localZ = z & 15;
                                if (BuildConfig.DEBUG && profile != null) {
                                    phaseStart = System.nanoTime();
                                }
                                if (precomputedMaterialPath) {
                                    coordinateAccess.adrenaline$updateZCoordinate(z);
                                } else {
                                    noiseChunk.updateForZ(z, zLerp[zInCell]);
                                }
                                if (BuildConfig.DEBUG && profile != null) {
                                    profile.add(NoiseSubstage.INTERPOLATION, System.nanoTime() - phaseStart);
                                    phaseStart = System.nanoTime();
                                }
                                BlockState state;
                                if (directMaterialPath) {
                                    int currentMaterialIndex = materialIndex++;
                                    double density = finalDensityValues[currentMaterialIndex];
                                    state = density > 0.0D ? null : aquifer.computeSubstance(noiseChunk, density);
                                    if (state == null) {
                                        state = y >= -60 && y <= 50 ? materialAccess.adrenaline$calculateOre(noiseChunk, currentMaterialIndex) : null;
                                        if (state == null) {
                                            state = defaultBlock;
                                        }
                                    }
                                } else {
                                    state = noiseChunkAccessor.adrenaline$getInterpolatedState();
                                    if (state == null) {
                                        state = defaultBlock;
                                    }
                                }

                                state = this.debugPreliminarySurfaceLevel(noiseChunk, x, y, z, state);
                                if (BuildConfig.DEBUG && profile != null) {
                                    profile.add(NoiseSubstage.BLOCK_STATE, System.nanoTime() - phaseStart);
                                    phaseStart = System.nanoTime();
                                }
                                if (state == AIR || SharedConstants.debugVoidTerrain(chunkPos)) {
                                    if (BuildConfig.DEBUG && profile != null) {
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
                                if (BuildConfig.DEBUG && profile != null) {
                                    profile.add(NoiseSubstage.BLOCK_WRITE, System.nanoTime() - phaseStart);
                                }
                            }
                        }
                    }
                }
            }

            if (cellX + 1 < cellCountX) {
                if (BuildConfig.DEBUG && profile != null) {
                    phaseStart = System.nanoTime();
                }
                noiseChunk.swapSlices();
                if (BuildConfig.DEBUG && profile != null) {
                    profile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - phaseStart);
                }
            }
        }

        if (BuildConfig.DEBUG && profile != null) {
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
        if (BuildConfig.DEBUG && profile != null) {
            profile.add(NoiseSubstage.FINALIZE, System.nanoTime() - phaseStart);
            WorldgenStageStats.finishNoiseProfile(profile);
        }
        cir.setReturnValue(chunk);
    }
}
