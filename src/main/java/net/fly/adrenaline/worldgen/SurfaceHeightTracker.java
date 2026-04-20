package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SurfaceHeightTracker {

    private final int[] worldSurface = new int[256];
    private final int[] oceanFloor = new int[256];

    public SurfaceHeightTracker(ChunkAccess chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = index(x, z);
                this.worldSurface[index] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1;
                this.oceanFloor[index] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) + 1;
            }
        }
    }

    public void set(int x, int z, int worldSurface, int oceanFloor) {
        int index = index(x, z);
        this.worldSurface[index] = worldSurface;
        this.oceanFloor[index] = oceanFloor;
    }

    public int worldSurface(int x, int z) {
        return this.worldSurface[index(x, z)];
    }

    public int oceanFloor(int x, int z) {
        return this.oceanFloor[index(x, z)];
    }

    private static int index(int x, int z) {
        return (z & 15) * 16 + (x & 15);
    }
}
