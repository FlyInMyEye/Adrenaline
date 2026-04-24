package net.fly.adrenaline.worldgen;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class SurfaceSystemOptimizer {

    private SurfaceSystemOptimizer() {
    }

    public static void buildSurface(SurfaceSystem system, BlockState defaultBlock, StonePredicate stonePredicate, BadlandsExtension badlandsExtension, FrozenOceanExtension frozenOceanExtension, RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource) {
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        MutableBlockPos biomePos = new MutableBlockPos();
        MutableBlockPos extensionPos = new MutableBlockPos();
        MutableBlockPos postProcessPos = new MutableBlockPos();
        Set<LevelChunkSection> dirtySections = new HashSet<>();
        FastSurfaceColumn column = new FastSurfaceColumn(chunk, postProcessPos, dirtySections);
        SurfaceRulePipeline surfaceRulePipeline = SurfaceRulesContextFactory.createPipeline(system, randomState, chunk, noiseChunk, biomeManager::getBiome, biomeRegistry, context, ruleSource);
        SurfaceHeightTracker heightTracker = new SurfaceHeightTracker(chunk);
        int minBuildHeight = chunk.getMinBuildHeight();
        int maxBuildHeight = chunk.getMaxBuildHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockX = minBlockX + localX;
                int blockZ = minBlockZ + localZ;
                int topY = heightTracker.worldSurface(localX, localZ);
                int biomeY = useLegacyRandomSource ? 0 : topY;
                Holder<Biome> biomeHolder = biomeManager.getBiome(biomePos.set(blockX, biomeY, blockZ));

                column.resetColumn(localX, localZ, blockX, blockZ);

                if (biomeHolder.is(Biomes.ERODED_BADLANDS)) {
                    badlandsExtension.apply(column, blockX, blockZ, topY, chunk);
                }

                int scanTopY = column.findWorldSurface(maxBuildHeight - 1);
                surfaceRulePipeline.updateXZ(blockX, blockZ);
                int stoneDepthAbove = 0;
                int waterHeight = Integer.MIN_VALUE;
                int minStoneY = Integer.MAX_VALUE;

                for (int y = scanTopY - 1; y >= minBuildHeight; y--) {
                    BlockState state = column.getBlock(y);
                    if (state.isAir()) {
                        stoneDepthAbove = 0;
                        waterHeight = Integer.MIN_VALUE;
                        continue;
                    }

                    if (!state.getFluidState().isEmpty()) {
                        if (waterHeight == Integer.MIN_VALUE) {
                            waterHeight = y + 1;
                        }
                        continue;
                    }

                    if (minStoneY >= y) {
                        minStoneY = DimensionType.WAY_BELOW_MIN_Y;
                        for (int below = y - 1; below >= minBuildHeight - 1; below--) {
                            if (!stonePredicate.test(column.getBlock(below))) {
                                minStoneY = below + 1;
                                break;
                            }
                        }
                    }

                    stoneDepthAbove++;
                    int stoneDepthBelow = y - minStoneY + 1;
                    surfaceRulePipeline.updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, blockX, y, blockZ);
                    if (state != defaultBlock) {
                        continue;
                    }

                    BlockState surfaceState = surfaceRulePipeline.tryApply(blockX, y, blockZ);
                    if (surfaceState != null) {
                        column.setBlock(y, surfaceState);
                    }
                }

                if (biomeHolder.is(Biomes.FROZEN_OCEAN) || biomeHolder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    frozenOceanExtension.apply(surfaceRulePipeline.getMinSurfaceLevel(), biomeHolder.value(), column, extensionPos, blockX, blockZ, topY);
                }

                heightTracker.set(localX, localZ, column.findWorldSurface(maxBuildHeight - 1), column.findOceanFloor(maxBuildHeight - 1));
            }
        }

        for (LevelChunkSection dirtySection : dirtySections) {
            dirtySection.recalcBlockCounts();
        }

        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                FastHeightmap.setRawHeight(worldSurface, localX, localZ, heightTracker.worldSurface(localX, localZ));
                FastHeightmap.setRawHeight(oceanFloor, localX, localZ, heightTracker.oceanFloor(localX, localZ));
            }
        }

        chunk.initializeLightSources();
    }

    @FunctionalInterface
    public interface StonePredicate {
        boolean test(BlockState state);
    }

    @FunctionalInterface
    public interface BadlandsExtension {
        void apply(BlockColumn column, int blockX, int blockZ, int topY, LevelHeightAccessor heightAccessor);
    }

    @FunctionalInterface
    public interface FrozenOceanExtension {
        void apply(int minSurfaceLevel, Biome biome, BlockColumn column, MutableBlockPos pos, int blockX, int blockZ, int topY);
    }
}
