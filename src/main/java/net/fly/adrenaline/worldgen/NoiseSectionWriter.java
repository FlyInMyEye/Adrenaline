package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class NoiseSectionWriter {

    private static final int SECTION_SIZE = 4096;
    private static final int MAX_LOCAL_STATES = 256;

    private final LevelChunkSection section;
    private final short[] stateIds = new short[SECTION_SIZE];
    private final BlockState[] palette = new BlockState[MAX_LOCAL_STATES];
    private final int[] firstIndices = new int[MAX_LOCAL_STATES];
    private int paletteSize;
    private BlockState lastState;
    private short lastStateId;
    private boolean direct;

    public NoiseSectionWriter(LevelChunkSection section) {
        this.section = section;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (this.direct) {
            this.section.getStates().getAndSetUnchecked(x, y, z, state);
            return;
        }

        int index = (y << 8) | (z << 4) | x;
        short stateId = this.findStateId(state);
        if (stateId == 0) {
            if (this.paletteSize == MAX_LOCAL_STATES) {
                this.flushStaged();
                this.direct = true;
                this.section.getStates().getAndSetUnchecked(x, y, z, state);
                return;
            }

            int paletteIndex = this.paletteSize++;
            this.palette[paletteIndex] = state;
            this.firstIndices[paletteIndex] = index;
            stateId = (short) (paletteIndex + 1);
        }

        this.lastState = state;
        this.lastStateId = stateId;
        this.stateIds[index] = stateId;
    }

    public void finish() {
        if (!this.direct) {
            this.flushStaged();
        }
        this.section.recalcBlockCounts();
    }

    private short findStateId(BlockState state) {
        if (state == this.lastState) {
            return this.lastStateId;
        }

        for (int i = 0; i < this.paletteSize; i++) {
            if (this.palette[i] == state) {
                return (short) (i + 1);
            }
        }
        return 0;
    }

    private void flushStaged() {
        PalettedContainer<BlockState> states = this.section.getStates();
        for (int i = 0; i < this.paletteSize; i++) {
            int index = this.firstIndices[i];
            states.getAndSetUnchecked(index & 15, index >> 8, index >> 4 & 15, this.palette[i]);
        }

        for (int index = 0; index < SECTION_SIZE; index++) {
            int stateId = this.stateIds[index] & 0xFFFF;
            if (stateId != 0) {
                states.getAndSetUnchecked(index & 15, index >> 8, index >> 4 & 15, this.palette[stateId - 1]);
            }
        }
    }
}
