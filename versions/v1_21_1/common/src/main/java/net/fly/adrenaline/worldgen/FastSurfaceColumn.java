package net.fly.adrenaline.worldgen;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FastSurfaceColumn implements BlockColumn {

    private final BlockColumn original;
    private final ChunkAccess chunk;
    private final LevelChunkSection[] sections;
    private final int minSection;
    private final MutableBlockPos position;
    private SurfaceColumnBuffer buffer;
    private final Heightmap worldSurface;
    private final Heightmap oceanFloor;
    private final int minY;
    private final int maxY;
    private int x = Integer.MIN_VALUE;
    private int z;
    private int surface;
    private int floor;

    public FastSurfaceColumn(BlockColumn original, ChunkAccess chunk, MutableBlockPos position, boolean buffered) {
        this.original = original;
        this.chunk = chunk;
        this.sections = chunk.getSections();
        this.minSection = chunk.getMinSection();
        this.position = position;
        this.minY = chunk.getMinBuildHeight();
        this.maxY = chunk.getMaxBuildHeight();
        this.buffer = buffered ? new SurfaceColumnBuffer(this.sections, this.minY) : null;
        this.worldSurface = buffered ? chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG) : null;
        this.oceanFloor = buffered ? chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG) : null;
    }

    public void disableBuffering() {
        this.finish();
        this.buffer = null;
    }

    public boolean buffered() {
        return this.buffer != null;
    }

    private void select() {
        int nextX = this.position.getX() & 15;
        int nextZ = this.position.getZ() & 15;
        if (this.x == nextX && this.z == nextZ) {
            return;
        }
        if (this.x != Integer.MIN_VALUE) {
            this.buffer.store();
        }
        this.x = nextX;
        this.z = nextZ;
        this.surface = this.worldSurface.getFirstAvailable(this.x, this.z);
        this.floor = this.oceanFloor.getFirstAvailable(this.x, this.z);
        this.buffer.load(this.x, this.z);
    }

    @Override
    public BlockState getBlock(int y) {
        if (this.buffer == null) {
            this.position.setY(y);
            if (y < this.minY || y >= this.maxY) {
                return Blocks.VOID_AIR.defaultBlockState();
            }
            LevelChunkSection section = this.sections[(y >> 4) - this.minSection];
            return section.hasOnlyAir() ? Blocks.AIR.defaultBlockState()
                : section.getBlockState(this.position.getX() & 15, y & 15, this.position.getZ() & 15);
        }
        this.select();
        return this.buffer.get(y);
    }

    @Override
    public void setBlock(int y, BlockState state) {
        if (this.buffer == null) {
            this.original.setBlock(y, state);
            return;
        }
        if (y < this.minY || y >= this.maxY) {
            return;
        }
        this.select();
        BlockState previous = this.buffer.get(y);
        if (previous != state) {
            this.buffer.set(y, state);
            if (!state.isAir()) {
                this.surface = Math.max(this.surface, y + 1);
            } else if (y + 1 == this.surface) {
                this.surface = this.findHeight(y - 1, false);
            }
            if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
                this.floor = Math.max(this.floor, y + 1);
            } else if (y + 1 == this.floor && Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(previous)) {
                this.floor = this.findHeight(y - 1, true);
            }
            FastHeightmap.setRawHeight(this.worldSurface, this.x, this.z, this.surface);
            FastHeightmap.setRawHeight(this.oceanFloor, this.x, this.z, this.floor);
        }
        if (!state.getFluidState().isEmpty()) {
            this.chunk.markPosForPostprocessing(this.position.setY(y));
        }
    }

    public int defaultBottom(int y, int limit, BlockState defaultBlock) {
        this.select();
        int bottom = y;
        while (bottom > limit && this.buffer.get(bottom - 1) == defaultBlock) {
            bottom--;
        }
        return bottom;
    }

    public void fill(int bottom, int top, BlockState state) {
        this.select();
        bottom = Math.max(bottom, this.minY);
        top = Math.min(top, this.maxY - 1);
        if (bottom > top) {
            return;
        }
        this.buffer.fill(bottom, top, state);
        if (!state.isAir()) {
            this.surface = Math.max(this.surface, top + 1);
        } else if (this.surface > bottom && this.surface <= top + 1) {
            this.surface = this.findHeight(bottom - 1, false);
        }
        if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
            this.floor = Math.max(this.floor, top + 1);
        } else if (this.floor > bottom && this.floor <= top + 1) {
            this.floor = this.findHeight(bottom - 1, true);
        }
        FastHeightmap.setRawHeight(this.worldSurface, this.x, this.z, this.surface);
        FastHeightmap.setRawHeight(this.oceanFloor, this.x, this.z, this.floor);
        if (!state.getFluidState().isEmpty()) {
            for (int y = top; y >= bottom; y--) {
                this.chunk.markPosForPostprocessing(this.position.setY(y));
            }
        }
    }

    private int findHeight(int y, boolean floor) {
        for (; y >= this.minY; y--) {
            BlockState state = this.buffer.get(y);
            if (floor ? Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state) : !state.isAir()) {
                return y + 1;
            }
        }
        return this.minY;
    }

    public void finish() {
        if (this.buffer != null && this.x != Integer.MIN_VALUE) {
            this.buffer.store();
            this.buffer.commit();
            this.chunk.initializeLightSources();
        }
    }
}
