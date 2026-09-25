package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;

public final class SurfaceColumnBuffer {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final LevelChunkSection[] sections;
    private final BlockState[][] decoded;
    private final boolean[] dirty;
    private final boolean[] columnDirty;
    private final boolean[] initiallyAir;
    private final BlockState[] column;
    private final int[] stoneBottom;
    private final int[] defaultBottom;
    private final int minY;
    private int columnOffset;

    public SurfaceColumnBuffer(LevelChunkSection[] sections, int minY) {
        this.sections = sections;
        this.minY = minY;
        this.decoded = new BlockState[sections.length][];
        this.dirty = new boolean[sections.length];
        this.columnDirty = new boolean[sections.length];
        this.initiallyAir = new boolean[sections.length];
        for (int i = 0; i < sections.length; i++) {
            this.initiallyAir[i] = !sections[i].getStates().maybeHas(state -> state != AIR);
        }
        this.column = new BlockState[sections.length * 16];
        this.stoneBottom = new int[this.column.length];
        this.defaultBottom = new int[this.column.length];
    }

    public void load(int x, int z) {
        this.columnOffset = (z * 16 + x) * 16;
        for (int section = 0; section < this.sections.length; section++) {
            BlockState[] states = this.decoded[section];
            if (states == null && !this.initiallyAir[section]) {
                states = SectionPaletteBuilder.unpackColumns(this.sections[section].getStates());
                this.decoded[section] = states;
            }
            if (states == null) {
                Arrays.fill(this.column, section * 16, section * 16 + 16, AIR);
            } else {
                System.arraycopy(states, this.columnOffset, this.column, section * 16, 16);
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

    public void prepareRuns(BlockState defaultBlock, SurfaceSystemOptimizer.StonePredicate predicate, int top) {
        int nextStoneBottom = predicate.test(AIR) ? DimensionType.WAY_BELOW_MIN_Y : this.minY;
        int nextDefaultBottom = this.minY;
        int end = Math.min(this.column.length, top - this.minY);
        for (int index = 0; index < end; index++) {
            int y = this.minY + index;
            BlockState state = this.column[index];
            this.stoneBottom[index] = nextStoneBottom;
            if (!predicate.test(state)) {
                nextStoneBottom = y + 1;
            }
            if (state != defaultBlock) {
                nextDefaultBottom = y + 1;
            }
            this.defaultBottom[index] = nextDefaultBottom;
        }
    }

    public int stoneBottom(int y) {
        return this.stoneBottom[y - this.minY];
    }

    public int defaultBottom(int y) {
        return this.defaultBottom[y - this.minY];
    }

    public void store() {
        for (int section = 0; section < this.sections.length; section++) {
            if (!this.columnDirty[section]) {
                continue;
            }
            BlockState[] states = this.decoded[section];
            if (states == null) {
                states = new BlockState[4096];
                Arrays.fill(states, AIR);
                this.decoded[section] = states;
            }
            System.arraycopy(this.column, section * 16, states, this.columnOffset, 16);
            this.dirty[section] = true;
            this.columnDirty[section] = false;
        }
    }

    public void commit() {
        for (int section = 0; section < this.sections.length; section++) {
            if (!this.dirty[section]) {
                continue;
            }
            BlockState[] states = this.decoded[section];
            SectionTerrainBuffer packed = new SectionTerrainBuffer(AIR);
            BlockState previous = null;
            int id = 0;
            for (int column = 0; column < 256; column++) {
                for (int y = 0; y < 16; y++) {
                    BlockState state = states[column * 16 + y];
                    if (state != previous) {
                        id = packed.id(state);
                        previous = state;
                    }
                    packed.set(y * 256 + column, id, false);
                }
            }
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
