package net.fly.adrenaline.worldgen;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.MixinNoiseChunkAccessor;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseProfile;
import net.fly.adrenaline.util.WorldgenStageStats.NoiseSubstage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class SectionTerrainGenerator {

    private static final boolean ENABLED = BuildConfig.DEBUG && Boolean.parseBoolean(System.getProperty("adrenaline.sectionTerrain", "true"));
    private static final int VERIFY_INTERVAL = Math.max(0, Integer.getInteger("adrenaline.sectionVerifyInterval", 64));
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicBoolean VERIFIED = new AtomicBoolean(true);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    private SectionTerrainGenerator() {
    }

    public static boolean enabled() {
        return ENABLED && VERIFIED.get();
    }

    public static boolean supports(ChunkAccess chunk, NoiseChunk noise, BlockState defaultBlock, int minCellY, int cellCountY) {
        if (!enabled() || !AdrenalineConfig.noiseChunkOptimizationsEnabled() || !AdrenalineConfig.nativeAquiferBatchingEnabled()
            || !SectionPaletteBuilder.available() || chunk.isUpgrading() || noise.getBlender() != Blender.empty()
            || SharedConstants.debugVoidTerrain(chunk.getPos())
            || defaultBlock.isAir() || !defaultBlock.getFluidState().isEmpty()
            || (chunk.getMinBuildHeight() & 15) != 0 || minCellY * 8 != chunk.getMinBuildHeight() || cellCountY * 8 != chunk.getHeight()
            || !(noise instanceof AdrenalineNoiseChunkMaterialAccess material) || !material.adrenaline$hasPrecomputedMaterialPath()
            || !(noise.aquifer() instanceof AdrenalineNativeAquiferAccess)
            || !(noise.aquifer() instanceof AdrenalineFastAquiferAccess aquifer) || !aquifer.adrenaline$supportsPrecomputedMaterials()) {
            return false;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getStates().maybeHas(state -> state != AIR)) {
                return false;
            }
        }
        return ((AdrenalineSectionNoiseAccess) noise).adrenaline$supportsSectionTraversal(minCellY, cellCountY);
    }

    public static void fill(ChunkAccess chunk, NoiseChunk noise, BlockState defaultBlock, int minCellY, int cellCountY,
                            Supplier<NoiseChunk> referenceFactory, NoiseProfile noiseProfile) {
        boolean corrected = false;
        SectionTerrainReference reference = null;
        if (VERIFY_INTERVAL > 0 && SEQUENCE.getAndIncrement() % VERIFY_INTERVAL == 0L) {
            reference = SectionTerrainReference.generate(chunk, referenceFactory.get(), defaultBlock, minCellY, cellCountY);
        }
        AdrenalineSectionNoiseAccess sectionNoise = (AdrenalineSectionNoiseAccess) noise;
        AdrenalineNoiseChunkMaterialAccess material = (AdrenalineNoiseChunkMaterialAccess) noise;
        AdrenalineNativeAquiferAccess aquifer = (AdrenalineNativeAquiferAccess) noise.aquifer();
        WorldgenHeightmapTracker heights = new WorldgenHeightmapTracker(chunk.getMinBuildHeight());
        MutableBlockPos position = new MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        long sampleStarted = noiseProfile == null ? 0L : System.nanoTime();
        noise.initializeForFirstCellX();
        try (SectionNoiseLattice lattice = new SectionNoiseLattice(((MixinNoiseChunkAccessor) noise).adrenaline$interpolators(), 4)) {
            lattice.capture(0, true);
            for (int cellX = 0; cellX < 4; cellX++) {
                noise.advanceCellX(cellX);
                lattice.capture(cellX + 1, false);
                if (cellX < 3) {
                    noise.swapSlices();
                }
            }
            if (noiseProfile != null) {
                noiseProfile.add(NoiseSubstage.SLICE_SAMPLING, System.nanoTime() - sampleStarted);
            }
            for (int sectionIndex = chunk.getSectionsCount() - 1; sectionIndex >= 0; sectionIndex--) {
                int sectionY = chunk.getMinBuildHeight() + sectionIndex * 16;
                SectionTerrainBuffer buffer = new SectionTerrainBuffer(defaultBlock);
                for (int cellX = 0; cellX < 4; cellX++) {
                    lattice.select(cellX);
                    sectionNoise.adrenaline$setSectionCellX(cellX);
                    for (int cellZ = 0; cellZ < 4; cellZ++) {
                        for (int half = 1; half >= 0; half--) {
                            long startedNanos = noiseProfile == null ? 0L : System.nanoTime();
                            noise.selectCellYZ(sectionIndex * 2 + half, cellZ);
                            double[] density = material.adrenaline$finalDensityValues();
                            boolean prepared = aquifer.adrenaline$prepareNativeMaterials(noise, density, baseX + cellX * 4,
                                sectionY + half * 8, baseZ + cellZ * 4, 4, 8);
                            if (noiseProfile != null) {
                                long elapsedNanos = System.nanoTime() - startedNanos;
                                long oreNanos = material.adrenaline$consumeMaterialArrayFillNanos();
                                noiseProfile.add(NoiseSubstage.CELL_CACHE, Math.max(0L, elapsedNanos - oreNanos));
                                noiseProfile.add(NoiseSubstage.ORE_CACHE, oreNanos);
                            }
                            startedNanos = noiseProfile == null ? 0L : System.nanoTime();
                            fillCell(buffer, noise, material, aquifer, defaultBlock, density, prepared,
                                cellX * 4, sectionY + half * 8, cellZ * 4, baseX, baseZ);
                            if (noiseProfile != null) {
                                noiseProfile.add(NoiseSubstage.BLOCK_STATE, System.nanoTime() - startedNanos);
                            }
                        }
                    }
                }
                if (reference != null) {
                    SectionTerrainBuffer checked = reference.verify(sectionIndex, buffer, defaultBlock);
                    corrected |= checked != buffer;
                    buffer = checked;
                }
                long startedNanos = noiseProfile == null ? 0L : System.nanoTime();
                buffer.commit(chunk.getSection(sectionIndex));
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int surface = buffer.worldSurface(x, z);
                        if (surface >= 0) {
                            heights.record(x, sectionY + surface, z, buffer.state(surface << 8 | z << 4 | x));
                        }
                        int floor = buffer.oceanFloor(x, z);
                        if (floor >= 0) {
                            heights.record(x, sectionY + floor, z, buffer.state(floor << 8 | z << 4 | x));
                        }
                    }
                }
                for (int offset : buffer.postprocessing()) {
                    position.set(baseX + (offset & 15), sectionY + (offset >> 8), baseZ + (offset >> 4 & 15));
                    chunk.markPosForPostprocessing(position);
                }
                if (noiseProfile != null) {
                    noiseProfile.add(NoiseSubstage.BLOCK_WRITE, System.nanoTime() - startedNanos);
                }
            }
        } finally {
            noise.stopInterpolation();
        }
        long startedNanos = noiseProfile == null ? 0L : System.nanoTime();
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                FastHeightmap.setRawHeight(surface, x, z, heights.worldSurface(x, z));
                FastHeightmap.setRawHeight(floor, x, z, heights.oceanFloor(x, z));
            }
        }
        chunk.initializeLightSources();
        if (noiseProfile != null) {
            noiseProfile.add(NoiseSubstage.FINALIZE, System.nanoTime() - startedNanos);
        }
        if (corrected) {
            VERIFIED.set(false);
            GlobalCommon.LOGGER.error("Section terrain verification corrected block or fluid mismatches in chunk {}", chunk.getPos());
            GlobalCommon.LOGGER.error("Section terrain disabled for subsequent chunks; already running chunks may still finish the experimental path");
        }
    }

    private static void fillCell(SectionTerrainBuffer buffer, NoiseChunk noise, AdrenalineNoiseChunkMaterialAccess material,
                                 AdrenalineNativeAquiferAccess nativeAquifer, BlockState defaultBlock, double[] density, boolean prepared,
                                 int localX, int baseY, int localZ, int chunkX, int chunkZ) {
        AdrenalineNoiseChunkCoordinateAccess coordinates = (AdrenalineNoiseChunkCoordinateAccess) noise;
        Aquifer aquifer = noise.aquifer();
        int defaultId = buffer.id(defaultBlock);
        int waterId = buffer.id(WATER);
        int lavaId = buffer.id(LAVA);
        int index = 0;
        for (int y = 7; y >= 0; y--) {
            int blockY = baseY + y;
            boolean oreHeight = blockY >= -60 && blockY <= 50;
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++, index++) {
                    int offset = (blockY & 15) << 8 | (localZ + z) << 4 | localX + x;
                    int code = prepared ? nativeAquifer.adrenaline$nativeMaterialAt(index) : 0;
                    int type = code & 3;
                    if (prepared && type == 1) {
                        continue;
                    }
                    if (prepared && type != 0) {
                        buffer.set(offset, type == 2 ? waterId : lavaId, (code & 4) != 0);
                        continue;
                    }
                    if (prepared && !oreHeight) {
                        buffer.set(offset, defaultId, false);
                        continue;
                    }
                    coordinates.adrenaline$updateYCoordinate(blockY);
                    coordinates.adrenaline$updateXCoordinate(chunkX + localX + x);
                    coordinates.adrenaline$updateZCoordinate(chunkZ + localZ + z);
                    BlockState state = !prepared && !(density[index] > 0.0D) ? aquifer.computeSubstance(noise, density[index]) : null;
                    if (state == null) {
                        state = oreHeight ? material.adrenaline$calculateOre(noise, index) : null;
                        if (state == null) {
                            state = defaultBlock;
                        }
                    }
                    if (state != AIR) {
                        buffer.set(offset, buffer.id(state), !prepared && aquifer.shouldScheduleFluidUpdate());
                    }
                }
            }
        }
    }
}
