package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SurfaceBiomeCache implements Function<BlockPos, Holder<Biome>> {

    private final BlockPos.MutableBlockPos lookup = new BlockPos.MutableBlockPos();
    private Function<BlockPos, Holder<Biome>> source;
    private Holder<Biome>[] values;
    private int[] generations;
    private int generation;
    private int minY;
    private int x;
    private int z;

    @SuppressWarnings("unchecked")
    public void rebind(ChunkAccess chunk, Function<BlockPos, Holder<Biome>> source) {
        this.source = source;
        this.minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        if (this.values == null || this.values.length != height) {
            this.values = (Holder<Biome>[]) new Holder<?>[height];
            this.generations = new int[height];
        } else {
            Arrays.fill(this.values, null);
            Arrays.fill(this.generations, 0);
        }
        this.generation = 1;
    }

    public void clear() {
        this.source = null;
        if (this.values != null) {
            Arrays.fill(this.values, null);
            Arrays.fill(this.generations, 0);
        }
    }

    public void column(int x, int z) {
        this.x = x;
        this.z = z;
        if (this.generation == Integer.MAX_VALUE) {
            Arrays.fill(this.generations, 0);
            this.generation = 1;
        } else {
            this.generation++;
        }
    }

    @Override
    public Holder<Biome> apply(BlockPos position) {
        int index = position.getY() - this.minY;
        if (position.getX() != this.x || position.getZ() != this.z || index < 0 || index >= this.values.length) {
            return this.source.apply(position);
        }
        if (this.generations[index] != this.generation) {
            this.values[index] = this.source.apply(position);
            this.generations[index] = this.generation;
        }
        return this.values[index];
    }

    public int certifiedBottom(int y, int runBottom) {
        Holder<Biome> current = this.apply(this.lookup.set(this.x, y, this.z));
        int bottom = y;
        int limit = Math.max(runBottom, y - 7);
        while (bottom > limit && this.apply(this.lookup.set(this.x, bottom - 1, this.z)) == current) {
            bottom--;
        }
        return bottom;
    }
}
