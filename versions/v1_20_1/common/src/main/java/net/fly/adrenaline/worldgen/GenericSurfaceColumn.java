package net.fly.adrenaline.worldgen;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;

public final class GenericSurfaceColumn implements BlockColumn {

    private final ChunkAccess chunk;
    private final MutableBlockPos pos;

    public GenericSurfaceColumn(ChunkAccess chunk, MutableBlockPos pos) {
        this.chunk = chunk;
        this.pos = pos;
    }

    public void resetColumn(int blockX, int blockZ) {
        this.pos.set(blockX, this.chunk.getMinBuildHeight(), blockZ);
    }

    @Override
    public BlockState getBlock(int y) {
        return this.chunk.getBlockState(this.pos.setY(y));
    }

    @Override
    public void setBlock(int y, BlockState state) {
        this.chunk.setBlockState(this.pos.setY(y), state, false);
    }
}
