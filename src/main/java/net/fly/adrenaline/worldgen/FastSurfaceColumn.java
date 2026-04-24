package net.fly.adrenaline.worldgen;

import java.util.Set;

import net.fly.adrenaline.mixin.MixinChunkAccessAccessor;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class FastSurfaceColumn implements BlockColumn {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ChunkAccess chunk;
    private final LevelChunkSection[] sections;
    private final MutableBlockPos postProcessPos;
    private final Set<LevelChunkSection> dirtySections;
    private final int minBuildHeight;
    private final int maxBuildHeight;

    private int localX;
    private int localZ;
    private int blockX;
    private int blockZ;
    private int worldSurface;
    private int oceanFloor;

    public FastSurfaceColumn(ChunkAccess chunk, MutableBlockPos postProcessPos, Set<LevelChunkSection> dirtySections) {
        this.chunk = chunk;
        this.sections = ((MixinChunkAccessAccessor) chunk).adrenaline$getSections();
        this.postProcessPos = postProcessPos;
        this.dirtySections = dirtySections;
        this.minBuildHeight = chunk.getMinBuildHeight();
        this.maxBuildHeight = chunk.getMaxBuildHeight();
    }

    public void resetColumn(int localX, int localZ, int blockX, int blockZ, int worldSurface, int oceanFloor) {
        this.localX = localX;
        this.localZ = localZ;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.worldSurface = worldSurface;
        this.oceanFloor = oceanFloor;
    }

    @Override
    public BlockState getBlock(int y) {
        if (y < this.minBuildHeight || y >= this.maxBuildHeight) {
            return AIR;
        }

        LevelChunkSection section = this.sections[this.chunk.getSectionIndex(y)];
        if (section.hasOnlyAir()) {
            return AIR;
        }

        return section.getBlockState(this.localX, y & 15, this.localZ);
    }

    @Override
    public void setBlock(int y, BlockState state) {
        if (y < this.minBuildHeight || y >= this.maxBuildHeight) {
            return;
        }

        LevelChunkSection section = this.sections[this.chunk.getSectionIndex(y)];
        BlockState previousState = section.hasOnlyAir() ? AIR : section.getBlockState(this.localX, y & 15, this.localZ);
        FastSectionAccess.writeUnchecked(section, this.localX, y & 15, this.localZ, state);
        this.dirtySections.add(section);
        this.updateHeights(y, previousState, state);

        if (!state.getFluidState().isEmpty()) {
            this.postProcessPos.set(this.blockX, y, this.blockZ);
            this.chunk.markPosForPostprocessing(this.postProcessPos);
        }
    }

    public int worldSurface() {
        return this.worldSurface;
    }

    public int oceanFloor() {
        return this.oceanFloor;
    }

    private void updateHeights(int y, BlockState previousState, BlockState newState) {
        if (previousState == newState) {
            return;
        }

        int height = y + 1;
        if (!newState.isAir() && height > this.worldSurface) {
            this.worldSurface = height;
        } else if (!previousState.isAir() && newState.isAir() && height == this.worldSurface) {
            this.worldSurface = this.findWorldSurface(y - 1);
        }

        boolean previousCountsForOceanFloor = !previousState.isAir() && previousState.getFluidState().isEmpty();
        boolean newCountsForOceanFloor = !newState.isAir() && newState.getFluidState().isEmpty();
        if (newCountsForOceanFloor && height > this.oceanFloor) {
            this.oceanFloor = height;
        } else if (previousCountsForOceanFloor && !newCountsForOceanFloor && height == this.oceanFloor) {
            this.oceanFloor = this.findOceanFloor(y - 1);
        }
    }

    public int findWorldSurface(int fromY) {
        for (int y = Math.min(fromY, this.maxBuildHeight - 1); y >= this.minBuildHeight; y--) {
            if (!this.getBlock(y).isAir()) {
                return y + 1;
            }
        }

        return this.minBuildHeight;
    }

    public int findOceanFloor(int fromY) {
        for (int y = Math.min(fromY, this.maxBuildHeight - 1); y >= this.minBuildHeight; y--) {
            BlockState state = this.getBlock(y);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return y + 1;
            }
        }

        return this.minBuildHeight;
    }
}
