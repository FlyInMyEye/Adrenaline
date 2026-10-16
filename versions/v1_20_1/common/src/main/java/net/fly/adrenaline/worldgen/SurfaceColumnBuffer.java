package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SurfaceColumnBuffer {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final LevelChunkSection[] sections;
    private final SectionTerrainBuffer[] decoded;
    private final boolean[] dirty;
    private final boolean[] columnDirty;
    private final BlockState[] uniform;
    private final BlockState[] column;
    private final int minY;
    private int columnOffset;

    public SurfaceColumnBuffer(LevelChunkSection[] sections, int minY) {
        this.sections = sections;
        this.minY = minY;
        this.decoded = new SectionTerrainBuffer[sections.length];
        this.dirty = new boolean[sections.length];
        this.columnDirty = new boolean[sections.length];
        this.uniform = new BlockState[sections.length];
        for (int i = 0; i < sections.length; i++) {
            this.uniform[i] = SectionPaletteBuilder.uniformState(sections[i].getStates());
        }
        this.column = new BlockState[sections.length * 16];
    }

    public void load(int x, int z) {
        this.columnOffset = z * 16 + x;
        for (int section = 0; section < this.sections.length; section++) {
            SectionTerrainBuffer states = this.decoded[section];
            if (states == null && this.uniform[section] == null) {
                states = new SectionTerrainBuffer(this.sections[section].getStates());
                this.decoded[section] = states;
            }
            if (states == null) {
                Arrays.fill(this.column, section * 16, section * 16 + 16, this.uniform[section]);
            } else {
                states.loadColumn(this.columnOffset, this.column, section * 16);
            }
        }
        Arrays.fill(this.columnDirty, false);
    }

    public BlockState get(int y) {
        int index = y - this.minY;
        return index < 0 || index >= this.column.length ? AIR : this.column[index];
    }

    public void set(int y, BlockState state) {
        int index = y - this.minY;
        if (index >= 0 && index < this.column.length && this.column[index] != state) {
            this.column[index] = state;
            this.columnDirty[index >> 4] = true;
        }
    }

    public void fill(int bottom, int top, BlockState state) {
        int start = Math.max(0, bottom - this.minY);
        int end = Math.min(this.column.length, top - this.minY + 1);
        for (int index = start; index < end; index++) {
            if (this.column[index] != state) {
                this.columnDirty[index >> 4] = true;
            }
        }
        if (start < end) {
            Arrays.fill(this.column, start, end, state);
        }
    }

    public void store() {
        for (int section = 0; section < this.sections.length; section++) {
            if (!this.columnDirty[section]) {
                continue;
            }
            SectionTerrainBuffer states = this.decoded[section];
            if (states == null) {
                states = SectionTerrainBuffer.filled(this.uniform[section]);
                this.uniform[section] = null;
                this.decoded[section] = states;
            }
            states.storeColumn(this.columnOffset, this.column, section * 16);
            this.dirty[section] = true;
            this.columnDirty[section] = false;
        }
    }

    public void commit() {
        for (int section = 0; section < this.sections.length; section++) {
            if (!this.dirty[section]) {
                continue;
            }
            SectionTerrainBuffer packed = this.decoded[section];
            LevelChunkSection target = this.sections[section];
            target.acquire();
            try {
                packed.commit(target);
            } finally {
                target.release();
            }
            this.dirty[section] = false;
        }
    }
}
