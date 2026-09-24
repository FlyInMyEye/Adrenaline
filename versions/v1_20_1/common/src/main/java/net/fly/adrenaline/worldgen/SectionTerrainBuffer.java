package net.fly.adrenaline.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.mixin.MixinLevelChunkSectionAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SectionTerrainBuffer {

    private final int[] ids = new int[4096];
    private final long[] fluidUpdates = new long[64];
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Integer> paletteIds = new IdentityHashMap<>();

    public SectionTerrainBuffer(BlockState defaultBlock) {
        this.id(Blocks.AIR.defaultBlockState());
        this.id(defaultBlock);
        this.id(Blocks.WATER.defaultBlockState());
        this.id(Blocks.LAVA.defaultBlockState());
    }

    public int id(BlockState state) {
        Integer existing = this.paletteIds.get(state);
        if (existing != null) {
            return existing;
        }
        int id = this.palette.size();
        this.palette.add(state);
        this.paletteIds.put(state, id);
        return id;
    }

    public void set(int index, int id, boolean fluidUpdate) {
        this.ids[index] = id;
        long mask = 1L << (index & 63);
        if (fluidUpdate && !this.palette.get(id).getFluidState().isEmpty()) {
            this.fluidUpdates[index >> 6] |= mask;
        } else {
            this.fluidUpdates[index >> 6] &= ~mask;
        }
    }

    public BlockState state(int index) {
        return this.palette.get(this.ids[index]);
    }

    public boolean fluidUpdate(int index) {
        return (this.fluidUpdates[index >> 6] & 1L << (index & 63)) != 0L;
    }

    public int[] postprocessing() {
        int count = 0;
        for (long word : this.fluidUpdates) {
            count += Long.bitCount(word);
        }
        int[] positions = new int[count];
        int next = 0;
        for (int word = 0; word < this.fluidUpdates.length; word++) {
            long bits = this.fluidUpdates[word];
            while (bits != 0L) {
                int offset = word * 64 + Long.numberOfTrailingZeros(bits);
                int x = offset & 15;
                int y = offset >> 8;
                int z = offset >> 4 & 15;
                int cell = ((x >> 2) * 4 + (z >> 2)) * 2 + (1 - (y >> 3));
                int rank = cell * 128 + (7 - (y & 7)) * 16 + (x & 3) * 4 + (z & 3);
                positions[next++] = rank << 12 | offset;
                bits &= bits - 1L;
            }
        }
        Arrays.sort(positions);
        for (int i = 0; i < positions.length; i++) {
            positions[i] &= 4095;
        }
        return positions;
    }

    public int worldSurface(int x, int z) {
        for (int y = 15; y >= 0; y--) {
            if (!this.state(y << 8 | z << 4 | x).isAir()) {
                return y;
            }
        }
        return -1;
    }

    public int oceanFloor(int x, int z) {
        for (int y = 15; y >= 0; y--) {
            BlockState state = this.state(y << 8 | z << 4 | x);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return y;
            }
        }
        return -1;
    }

    public void commit(LevelChunkSection section) {
        int[] frequencies = new int[this.palette.size()];
        for (int id : this.ids) {
            frequencies[id]++;
        }
        List<BlockState> compact = new ArrayList<>();
        int[] remap = new int[frequencies.length];
        Arrays.fill(remap, -1);
        int nonEmpty = 0;
        int tickingBlocks = 0;
        int tickingFluids = 0;
        for (int i = 0; i < frequencies.length; i++) {
            int count = frequencies[i];
            if (count == 0) {
                continue;
            }
            BlockState state = this.palette.get(i);
            remap[i] = compact.size();
            compact.add(state);
            if (!state.isAir()) {
                nonEmpty += count;
                if (state.isRandomlyTicking()) {
                    tickingBlocks += count;
                }
            }
            if (!state.getFluidState().isEmpty()) {
                nonEmpty += count;
                if (state.getFluidState().isRandomlyTicking()) {
                    tickingFluids += count;
                }
            }
        }
        int[] compactIds = new int[this.ids.length];
        for (int i = 0; i < compactIds.length; i++) {
            compactIds[i] = remap[this.ids[i]];
        }
        SectionPaletteBuilder.commit(section.getStates(), compact, compactIds);
        if (section instanceof MixinLevelChunkSectionAccessor access) {
            access.adrenaline$setNonEmptyBlockCount((short) nonEmpty);
            access.adrenaline$setTickingBlockCount((short) tickingBlocks);
            access.adrenaline$setTickingFluidCount((short) tickingFluids);
        } else {
            section.recalcBlockCounts();
        }
    }
}
