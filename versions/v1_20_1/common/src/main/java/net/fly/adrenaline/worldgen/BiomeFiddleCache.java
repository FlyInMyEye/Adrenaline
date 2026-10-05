package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import net.minecraft.util.LinearCongruentialGenerator;

public final class BiomeFiddleCache {
    private static final ThreadLocal<Cache> CACHE = new ThreadLocal<>();

    private BiomeFiddleCache() {
    }

    public static Scope open() {
        return new Scope(cache());
    }

    public static int selectCornerIfActive(long seed, int blockX, int blockY, int blockZ) {
        Cache cache = CACHE.get();
        return cache == null || cache.scopeDepth == 0 ? -1 : selectCorner(cache, seed, blockX, blockY, blockZ);
    }

    public static int selectCorner(long seed, int blockX, int blockY, int blockZ) {
        return selectCorner(cache(), seed, blockX, blockY, blockZ);
    }

    private static Cache cache() {
        Cache cache = CACHE.get();
        if (cache == null) {
            cache = new Cache();
            CACHE.set(cache);
        }
        return cache;
    }

    private static int selectCorner(Cache cache, long seed, int blockX, int blockY, int blockZ) {
        int x = blockX - 2;
        int y = blockY - 2;
        int z = blockZ - 2;
        cache.prepare(seed, x >> 2, y >> 2, z >> 2);
        double dx = (x & 3) / 4.0;
        double dy = (y & 3) / 4.0;
        double dz = (z & 3) / 4.0;
        double best = Double.POSITIVE_INFINITY;
        int winner = 0;
        for (int corner = 0; corner < 8; corner++) {
            int offset = corner * 3;
            double fx = ((corner & 4) == 0 ? dx : dx - 1.0) + cache.cell[offset];
            double fy = ((corner & 2) == 0 ? dy : dy - 1.0) + cache.cell[offset + 1];
            double fz = ((corner & 1) == 0 ? dz : dz - 1.0) + cache.cell[offset + 2];
            double distance = fz * fz + fy * fy + fx * fx;
            if (best > distance) {
                winner = corner;
                best = distance;
            }
        }
        return winner;
    }

    public static final class Scope implements AutoCloseable {
        private final Cache cache;

        private Scope(Cache cache) {
            this.cache = cache;
            cache.scopeDepth++;
        }

        @Override
        public void close() {
            this.cache.scopeDepth--;
        }
    }

    private static final class Cache {
        private static final int SIZE = 4096;
        private final int[] xs = new int[SIZE];
        private final int[] ys = new int[SIZE];
        private final int[] zs = new int[SIZE];
        private final boolean[] present = new boolean[SIZE];
        private final double[] offsets = new double[SIZE * 3];
        private final double[] cell = new double[24];
        private int scopeDepth;
        private long seed;
        private boolean cellPresent;
        private int cellX;
        private int cellY;
        private int cellZ;

        private void prepare(long seed, int x, int y, int z) {
            if (this.seed != seed) {
                Arrays.fill(this.present, false);
                this.cellPresent = false;
                this.seed = seed;
            }
            if (this.cellPresent && this.cellX == x && this.cellY == y && this.cellZ == z) {
                return;
            }
            for (int corner = 0; corner < 8; corner++) {
                this.copyCorner(x + ((corner & 4) == 0 ? 0 : 1),
                    y + ((corner & 2) == 0 ? 0 : 1), z + ((corner & 1) == 0 ? 0 : 1), corner * 3);
            }
            this.cellX = x;
            this.cellY = y;
            this.cellZ = z;
            this.cellPresent = true;
        }

        private void copyCorner(int x, int y, int z, int target) {
            int hash = x * 73428767 ^ y * 912367 ^ z * 4382891;
            int slot = (hash ^ (hash >>> 16)) & (SIZE - 1);
            int offset = slot * 3;
            if (!this.present[slot] || this.xs[slot] != x || this.ys[slot] != y || this.zs[slot] != z) {
                long state = LinearCongruentialGenerator.next(this.seed, x);
                state = LinearCongruentialGenerator.next(state, y);
                state = LinearCongruentialGenerator.next(state, z);
                state = LinearCongruentialGenerator.next(state, x);
                state = LinearCongruentialGenerator.next(state, y);
                state = LinearCongruentialGenerator.next(state, z);
                this.offsets[offset] = fiddle(state);
                state = LinearCongruentialGenerator.next(state, this.seed);
                this.offsets[offset + 1] = fiddle(state);
                state = LinearCongruentialGenerator.next(state, this.seed);
                this.offsets[offset + 2] = fiddle(state);
                this.xs[slot] = x;
                this.ys[slot] = y;
                this.zs[slot] = z;
                this.present[slot] = true;
            }
            this.cell[target] = this.offsets[offset];
            this.cell[target + 1] = this.offsets[offset + 1];
            this.cell[target + 2] = this.offsets[offset + 2];
        }

        private static double fiddle(long state) {
            return (Math.floorMod(state >> 24, 1024) / 1024.0 - 0.5) * 0.9;
        }
    }
}
