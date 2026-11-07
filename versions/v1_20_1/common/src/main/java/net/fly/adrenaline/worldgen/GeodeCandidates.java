package net.fly.adrenaline.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.NativeGeodeSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class GeodeCandidates implements Iterable<BlockPos> {

    private final int x;
    private final int y;
    private final int z;
    private final int size;
    private final int[] indices;
    private final int count;

    private GeodeCandidates(BlockPos min, int size, int[] indices, int count) {
        this.x = min.getX();
        this.y = min.getY();
        this.z = min.getZ();
        this.size = size;
        this.indices = indices;
        this.count = count;
    }

    public static GeodeCandidates create(BlockPos min, BlockPos max, List<Pair<BlockPos, Integer>> distribution, NormalNoise noise, GeodeConfiguration config) {
        if (!AdrenalineConfig.nativeGeodeFilteringEnabled()) {
            return null;
        }
        long side = (long) max.getX() - min.getX() + 1;
        if (side <= 0 || side > 40 || (long) max.getY() - min.getY() + 1 != side || (long) max.getZ() - min.getZ() + 1 != side
            || distribution.isEmpty() || distribution.size() > 64) {
            return null;
        }
        int[] points = new int[distribution.size() * 4];
        int at = 0;
        for (Pair<BlockPos, Integer> pair : distribution) {
            BlockPos point = pair.getFirst();
            long dx = (long) point.getX() - min.getX();
            long dy = (long) point.getY() - min.getY();
            long dz = (long) point.getZ() - min.getZ();
            if (Math.abs(dx) > 4096 || Math.abs(dy) > 4096 || Math.abs(dz) > 4096 || pair.getSecond() < 0) {
                return null;
            }
            points[at++] = (int) dx;
            points[at++] = (int) dy;
            points[at++] = (int) dz;
            points[at++] = pair.getSecond();
        }
        double threshold = 1.0D / Math.sqrt(config.geodeLayerSettings.outerLayer + (double) distribution.size() / config.outerWallDistance.getMaxValue());
        int size = (int) side;
        int[] indices = new int[size * size * size];
        int count = NativeGeodeSampler.filter(noise, min.getX(), min.getY(), min.getZ(), size, config.noiseMultiplier, threshold, points, indices);
        return count < 0 ? null : new GeodeCandidates(min, size, indices, count);
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return new Iterator<>() {
            private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            private int cursor;

            @Override
            public boolean hasNext() {
                return this.cursor < GeodeCandidates.this.count;
            }

            @Override
            public BlockPos next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                int index = GeodeCandidates.this.indices[this.cursor++];
                int size = GeodeCandidates.this.size;
                return this.position.set(GeodeCandidates.this.x + index % size,
                    GeodeCandidates.this.y + index / size % size, GeodeCandidates.this.z + index / (size * size));
            }
        };
    }
}
