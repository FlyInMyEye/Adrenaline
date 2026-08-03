package net.fly.adrenaline.worldgen;

import java.util.Arrays;

import net.minecraft.world.level.block.state.BlockState;

public final class WorldgenHeightmapTracker {

    private final int[] worldSurface = new int[256];
    private final int[] oceanFloor = new int[256];
    private final boolean[] worldSurfaceDone = new boolean[256];
    private final boolean[] oceanFloorDone = new boolean[256];

    public WorldgenHeightmapTracker(int minBuildHeight) {
        Arrays.fill(this.worldSurface, minBuildHeight);
        Arrays.fill(this.oceanFloor, minBuildHeight);
    }

    public void record(int x, int y, int z, BlockState state) {
        int index = (z & 15) * 16 + (x & 15);
        if (!this.worldSurfaceDone[index] && !state.isAir()) {
            this.worldSurfaceDone[index] = true;
            this.worldSurface[index] = y + 1;
        }
        if (!this.oceanFloorDone[index] && state.getFluidState().isEmpty() && !state.isAir()) {
            this.oceanFloorDone[index] = true;
            this.oceanFloor[index] = y + 1;
        }
    }

    public int worldSurface(int x, int z) {
        return this.worldSurface[(z & 15) * 16 + (x & 15)];
    }

    public int oceanFloor(int x, int z) {
        return this.oceanFloor[(z & 15) * 16 + (x & 15)];
    }
}
