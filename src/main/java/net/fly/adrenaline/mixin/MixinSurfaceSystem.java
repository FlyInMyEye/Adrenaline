package net.fly.adrenaline.mixin;

import java.util.HashSet;
import java.util.Set;

import net.fly.adrenaline.worldgen.FastHeightmap;
import net.fly.adrenaline.worldgen.FastSurfaceColumn;
import net.fly.adrenaline.worldgen.SurfaceRulePipeline;
import net.fly.adrenaline.worldgen.SurfaceHeightTracker;
import net.fly.adrenaline.worldgen.SurfaceRulesContextFactory;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SurfaceSystem.class)
public abstract class MixinSurfaceSystem {

    @Shadow @Final private BlockState defaultBlock;

    @Shadow
    protected abstract int getSurfaceDepth(int blockX, int blockZ);

    @Shadow
    protected abstract boolean isStone(BlockState state);

    @Shadow
    protected abstract void erodedBadlandsExtension(net.minecraft.world.level.chunk.BlockColumn column, int blockX, int blockZ, int topY, LevelHeightAccessor heightAccessor);

    @Shadow
    protected abstract void frozenOceanExtension(int minSurfaceLevel, Biome biome, net.minecraft.world.level.chunk.BlockColumn column, MutableBlockPos pos, int blockX, int blockZ, int topY);

    /**
     * @author Fly
     * @reason Direct section-local surface access path
     */
    @Overwrite
    public void buildSurface(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomeRegistry, boolean useLegacyRandomSource, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource) {
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        MutableBlockPos biomePos = new MutableBlockPos();
        MutableBlockPos extensionPos = new MutableBlockPos();
        MutableBlockPos postProcessPos = new MutableBlockPos();
        Set<LevelChunkSection> dirtySections = new HashSet<>();
        FastSurfaceColumn column = new FastSurfaceColumn(chunk, postProcessPos, dirtySections);
        SurfaceRulePipeline surfaceRulePipeline = SurfaceRulesContextFactory.createPipeline((SurfaceSystem) (Object) this, randomState, chunk, noiseChunk, biomeManager::getBiome, biomeRegistry, context, ruleSource);
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
                    this.erodedBadlandsExtension(column, blockX, blockZ, topY, chunk);
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
                        minStoneY = Integer.MIN_VALUE;
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
                            if (!this.isStone(column.getBlock(below))) {
                                minStoneY = below + 1;
                                break;
                            }
                        }
                    }

                    stoneDepthAbove++;
                    int stoneDepthBelow = y - minStoneY + 1;
                    surfaceRulePipeline.updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, blockX, y, blockZ);
                    if (state != this.defaultBlock) {
                        continue;
                    }

                    BlockState surfaceState = surfaceRulePipeline.tryApply(blockX, y, blockZ);
                    if (surfaceState != null) {
                        column.setBlock(y, surfaceState);
                    }
                }

                if (biomeHolder.is(Biomes.FROZEN_OCEAN) || biomeHolder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(surfaceRulePipeline.getMinSurfaceLevel(), biomeHolder.value(), column, extensionPos, blockX, blockZ, topY);
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
}
