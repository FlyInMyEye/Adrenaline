package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class SectionTerrainReference {

    private final BlockState[][] states;
    private final boolean[][] fluids;

    private SectionTerrainReference(int sections) {
        this.states = new BlockState[sections][4096];
        this.fluids = new boolean[sections][4096];
    }

    public static SectionTerrainReference generate(ChunkAccess chunk, NoiseChunk noise, BlockState defaultBlock, int minCellY, int cellCountY) {
        SectionTerrainReference result = new SectionTerrainReference(chunk.getSectionsCount());
        AdrenalineNoiseChunkMaterialAccess material = (AdrenalineNoiseChunkMaterialAccess) noise;
        AdrenalineNoiseChunkCoordinateAccess coordinates = (AdrenalineNoiseChunkCoordinateAccess) noise;
        AdrenalineCellGridAccess grid = (AdrenalineCellGridAccess) noise;
        Aquifer aquifer = noise.aquifer();
        AdrenalineNativeAquiferAccess nativeAquifer = (AdrenalineNativeAquiferAccess) aquifer;
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        noise.initializeForFirstCellX();
        try {
            for (int cellX = 0; cellX < 4; cellX++) {
                noise.advanceCellX(cellX);
                for (int cellZ = 0; cellZ < 4; cellZ++) {
                    for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                        noise.selectCellYZ(cellY, cellZ);
                        double[] densities = material.adrenaline$finalDensityValues();
                        boolean prepared = nativeAquifer.adrenaline$prepareNativeMaterials(noise, densities,
                            grid.adrenaline$getCellStartBlockX(), grid.adrenaline$getCellStartBlockY(), grid.adrenaline$getCellStartBlockZ(), 4, 8);
                        int index = 0;
                        for (int localY = 7; localY >= 0; localY--) {
                            int y = (minCellY + cellY) * 8 + localY;
                            coordinates.adrenaline$updateYCoordinate(y);
                            for (int localX = 0; localX < 4; localX++) {
                                int x = baseX + cellX * 4 + localX;
                                coordinates.adrenaline$updateXCoordinate(x);
                                for (int localZ = 0; localZ < 4; localZ++) {
                                    int z = baseZ + cellZ * 4 + localZ;
                                    coordinates.adrenaline$updateZCoordinate(z);
                                    boolean fluid = false;
                                    BlockState state;
                                    if (prepared) {
                                        byte code = nativeAquifer.adrenaline$nativeMaterialAt(index);
                                        state = switch (code & 3) {
                                            case 1 -> Blocks.AIR.defaultBlockState();
                                            case 2 -> Blocks.WATER.defaultBlockState();
                                            case 3 -> Blocks.LAVA.defaultBlockState();
                                            default -> null;
                                        };
                                        fluid = (code & 4) != 0;
                                    } else {
                                        state = densities[index] > 0.0D ? null : aquifer.computeSubstance(noise, densities[index]);
                                    }
                                    if (state == null) {
                                        state = y >= -60 && y <= 50 ? material.adrenaline$calculateOre(noise, index) : null;
                                        if (state == null) {
                                            state = defaultBlock;
                                        }
                                    }
                                    int sectionIndex = chunk.getSectionIndex(y);
                                    int sectionOffset = (y & 15) << 8 | (z & 15) << 4 | x & 15;
                                    result.states[sectionIndex][sectionOffset] = state;
                                    result.fluids[sectionIndex][sectionOffset] = !state.getFluidState().isEmpty()
                                        && (prepared ? fluid : aquifer.shouldScheduleFluidUpdate());
                                    index++;
                                }
                            }
                        }
                    }
                }
                if (cellX < 3) {
                    noise.swapSlices();
                }
            }
        } finally {
            noise.stopInterpolation();
        }
        return result;
    }

    public SectionTerrainBuffer verify(int sectionIndex, SectionTerrainBuffer actual, BlockState defaultBlock) {
        boolean matches = true;
        for (int i = 0; i < 4096; i++) {
            if (actual.state(i) != this.states[sectionIndex][i] || actual.fluidUpdate(i) != this.fluids[sectionIndex][i]) {
                matches = false;
                break;
            }
        }
        if (matches) {
            return actual;
        }
        SectionTerrainBuffer corrected = new SectionTerrainBuffer(defaultBlock);
        for (int i = 0; i < 4096; i++) {
            corrected.set(i, corrected.id(this.states[sectionIndex][i]), this.fluids[sectionIndex][i]);
        }
        return corrected;
    }
}
