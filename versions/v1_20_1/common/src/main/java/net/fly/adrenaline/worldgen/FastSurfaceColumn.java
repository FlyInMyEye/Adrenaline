package net.fly.adrenaline.worldgen;

import java.util.Set;

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
    private final SurfaceColumnBuffer buffer;
    private final int minBuildHeight;
    private final int maxBuildHeight;

    private int localX;
    private int localZ;
    private int blockX;
    private int blockZ;
    private int worldSurface;
    private int oceanFloor;

    public FastSurfaceColumn(ChunkAccess chunk, MutableBlockPos postProcessPos, Set<LevelChunkSection> dirtySections, boolean buffered) {
        this.chunk = chunk;
        this.sections = chunk.getSections();
        this.postProcessPos = postProcessPos;
        this.dirtySections = dirtySections;
        this.minBuildHeight = chunk.getMinBuildHeight();
        this.maxBuildHeight = chunk.getMaxBuildHeight();
        this.buffer = buffered ? new SurfaceColumnBuffer(this.sections, this.minBuildHeight) : null;
    }

    public void resetColumn(int localX, int localZ, int blockX, int blockZ, int worldSurface, int oceanFloor) {
        this.localX = localX;
        this.localZ = localZ;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.worldSurface = worldSurface;
        this.oceanFloor = oceanFloor;
        if (this.buffer != null) {
            this.buffer.load(localX, localZ);
        }
    }

    @Override
    public BlockState getBlock(int y) {
        if (y < this.minBuildHeight || y >= this.maxBuildHeight) {
            return AIR;
        }
        if (this.buffer != null) {
            return this.buffer.get(y);
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

        BlockState previousState = this.getBlock(y);
        if (this.buffer != null) {
            this.buffer.set(y, state);
        } else {
            LevelChunkSection section = this.sections[this.chunk.getSectionIndex(y)];
            FastSectionAccess.writeUnchecked(section, this.localX, y & 15, this.localZ, state);
            this.dirtySections.add(section);
        }
        this.updateHeights(y, previousState, state);

        if (!state.getFluidState().isEmpty()) {
            this.postProcessPos.set(this.blockX, y, this.blockZ);
            this.chunk.markPosForPostprocessing(this.postProcessPos);
        }
    }

    public void prepareRuns(BlockState defaultBlock, SurfaceSystemOptimizer.StonePredicate predicate, int top) {
        if (this.buffer != null) {
            this.buffer.prepareRuns(defaultBlock, predicate, top);
        }
    }

    public int stoneBottom(int y, SurfaceSystemOptimizer.StonePredicate predicate) {
        if (this.buffer != null) {
            return this.buffer.stoneBottom(y);
        }
        for (int below = y - 1; below >= this.minBuildHeight - 1; below--) {
            if (!predicate.test(this.getBlock(below))) {
                return below + 1;
            }
        }
        return net.minecraft.world.level.dimension.DimensionType.WAY_BELOW_MIN_Y;
    }

    public int defaultBottom(int y, BlockState defaultBlock) {
        if (this.buffer != null) {
            return this.buffer.defaultBottom(y);
        }
        int bottom = y;
        while (bottom > this.minBuildHeight && this.getBlock(bottom - 1) == defaultBlock) {
            bottom--;
        }
        return bottom;
    }

    public void setSpan(int bottom, int top, BlockState state) {
        if (this.buffer == null) {
            for (int y = top; y >= bottom; y--) {
                this.setBlock(y, state);
            }
            return;
        }
        bottom = Math.max(bottom, this.minBuildHeight);
        top = Math.min(top, this.maxBuildHeight - 1);
        if (bottom > top) {
            return;
        }
        this.buffer.fill(bottom, top, state);
        if (!state.isAir()) {
            this.worldSurface = Math.max(this.worldSurface, top + 1);
        } else if (this.worldSurface > bottom && this.worldSurface <= top + 1) {
            this.worldSurface = this.findWorldSurface(bottom - 1);
        }
        if (!state.isAir() && state.getFluidState().isEmpty()) {
            this.oceanFloor = Math.max(this.oceanFloor, top + 1);
        } else if (this.oceanFloor > bottom && this.oceanFloor <= top + 1) {
            this.oceanFloor = this.findOceanFloor(bottom - 1);
        }
        if (!state.getFluidState().isEmpty()) {
            for (int y = top; y >= bottom; y--) {
                this.chunk.markPosForPostprocessing(this.postProcessPos.set(this.blockX, y, this.blockZ));
            }
        }
    }

    public void finishColumn() {
        if (this.buffer != null) {
            this.buffer.store();
        }
    }

    public void finish() {
        if (this.buffer != null) {
            this.buffer.commit();
        } else {
            for (LevelChunkSection section : this.dirtySections) {
                section.recalcBlockCounts();
            }
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
