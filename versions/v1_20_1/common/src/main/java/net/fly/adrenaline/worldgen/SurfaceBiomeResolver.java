package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SurfaceBiomeResolver implements Function<BlockPos, Holder<Biome>> {

    private static final int WIDTH = 6;
    private BiomeManager source;
    private BiomeManager.NoiseBiomeSource noiseSource;
    private long zoomSeed;
    private Holder<Biome>[] biomes;
    private Holder<Biome>[] uniform;
    private boolean[] checked;
    private int minX;
    private int minY;
    private int minZ;
    private int height;

    public SurfaceBiomeResolver(BiomeManager source, ChunkAccess chunk) {
        this.rebind(source, chunk);
    }

    @SuppressWarnings("unchecked")
    public void rebind(BiomeManager source, ChunkAccess chunk) {
        this.clear();
        this.source = source;
        this.minX = (chunk.getPos().getMinBlockX() >> 2) - 1;
        this.minZ = (chunk.getPos().getMinBlockZ() >> 2) - 1;
        this.minY = (chunk.getMinBuildHeight() >> 2) - 1;
        this.height = (chunk.getMaxBuildHeight() >> 2) - this.minY + 2;
        int count = WIDTH * WIDTH * this.height;
        if (this.biomes == null || this.biomes.length != count) {
            this.biomes = (Holder<Biome>[]) new Holder<?>[count];
            this.uniform = (Holder<Biome>[]) new Holder<?>[count];
            this.checked = new boolean[count];
        }
        AdrenalineBiomeManagerSourceAccess access = (AdrenalineBiomeManagerSourceAccess) source;
        this.noiseSource = access.adrenaline$getNoiseBiomeSource();
        this.zoomSeed = access.adrenaline$getBiomeZoomSeed();
    }

    public void clear() {
        if (this.source != null) {
            Arrays.fill(this.biomes, null);
            Arrays.fill(this.uniform, null);
            Arrays.fill(this.checked, false);
            this.source = null;
            this.noiseSource = null;
        }
    }

    @Override
    public Holder<Biome> apply(BlockPos position) {
        Holder<Biome> result = this.resolve(position);
        return result == null ? this.source.getBiome(position) : result;
    }

    public Holder<Biome> resolve(BlockPos position) {
        int x = (position.getX() - 2) >> 2;
        int y = (position.getY() - 2) >> 2;
        int z = (position.getZ() - 2) >> 2;
        int localX = x - this.minX;
        int localY = y - this.minY;
        int localZ = z - this.minZ;
        if (localX >= 0 && localX < WIDTH - 1 && localY >= 0 && localY < this.height - 1
            && localZ >= 0 && localZ < WIDTH - 1) {
            int index = (localY * WIDTH + localZ) * WIDTH + localX;
            if (!this.checked[index]) {
                Holder<Biome> first = this.noiseBiome(x, y, z);
                boolean same = true;
                for (int dx = 0; dx < 2 && same; dx++) {
                    for (int dy = 0; dy < 2 && same; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            if (this.noiseBiome(x + dx, y + dy, z + dz) != first) {
                                same = false;
                                break;
                            }
                        }
                    }
                }
                this.uniform[index] = same ? first : null;
                this.checked[index] = true;
            }
            Holder<Biome> result = this.uniform[index];
            if (result != null) {
                return result;
            }
        }
        if (localX < 0 || localX >= WIDTH - 1 || localZ < 0 || localZ >= WIDTH - 1) {
            return null;
        }
        int corner = BiomeFiddleCache.selectCorner(this.zoomSeed, position.getX(), position.getY(), position.getZ());
        return this.noiseBiome(x + ((corner & 4) == 0 ? 0 : 1), y + ((corner & 2) == 0 ? 0 : 1), z + ((corner & 1) == 0 ? 0 : 1));
    }

    private Holder<Biome> noiseBiome(int x, int y, int z) {
        int localX = x - this.minX;
        int localY = y - this.minY;
        int localZ = z - this.minZ;
        if (localX < 0 || localX >= WIDTH || localY < 0 || localY >= this.height
            || localZ < 0 || localZ >= WIDTH) {
            return this.noiseSource.getNoiseBiome(x, y, z);
        }
        int index = (localY * WIDTH + localZ) * WIDTH + localX;
        Holder<Biome> result = this.biomes[index];
        if (result == null) {
            result = this.noiseSource.getNoiseBiome(x, y, z);
            this.biomes[index] = result;
        }
        return result;
    }
}
