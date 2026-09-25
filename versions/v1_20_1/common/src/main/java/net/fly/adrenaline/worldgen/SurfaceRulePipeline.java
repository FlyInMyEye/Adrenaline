package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandle;
import java.util.function.Function;

import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextMutable;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class SurfaceRulePipeline {

    private static final long INITIAL_LAST_UPDATE = Long.MIN_VALUE + 1L;

    private final Object context;
    private final MethodHandle tryApplyHandle;
    private final AdrenalineMixinSurfaceRulesContextApi contextApi;
    private final AdrenalineMixinSurfaceRulesContextMutable contextMutable;
    private final SurfaceRulePlan plan;
    private final boolean bufferedWrites;
    private int spanBottom;

    public SurfaceRulePipeline(Object surfaceRule, Object context, SurfaceRules.RuleSource source, WorldGenerationContext generation) {
        this.context = context;
        this.tryApplyHandle = SurfaceRulesContextFactory.createTryApplyHandle(surfaceRule);
        this.contextApi = (AdrenalineMixinSurfaceRulesContextApi) context;
        this.contextMutable = (AdrenalineMixinSurfaceRulesContextMutable) context;
        this.plan = SurfaceRulePlan.compile(source, context, generation, this.contextApi);
        this.bufferedWrites = SurfaceRulePlan.supportsBufferedWrites(source);
    }

    public boolean supportsBufferedWrites() {
        return this.bufferedWrites;
    }

    public void rebind(ChunkAccess chunk, NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter) {
        this.contextMutable.adrenaline$setChunk(chunk);
        this.contextMutable.adrenaline$setNoiseChunk(noiseChunk);
        this.contextMutable.adrenaline$setBiomeGetter(biomeGetter);
        this.contextMutable.adrenaline$setLastPreliminarySurfaceCellOrigin(Long.MAX_VALUE);
        this.contextMutable.adrenaline$setLastUpdateXZ(INITIAL_LAST_UPDATE);
        this.contextMutable.adrenaline$setLastSurfaceDepth2Update(INITIAL_LAST_UPDATE - 1L);
        this.contextMutable.adrenaline$setLastMinSurfaceLevelUpdate(INITIAL_LAST_UPDATE - 1L);
        this.contextMutable.adrenaline$setLastUpdateY(INITIAL_LAST_UPDATE);
        this.contextMutable.adrenaline$setBiome(null);
    }

    public void updateXZ(int blockX, int blockZ) {
        this.contextApi.adrenaline$updateXZ(blockX, blockZ);
    }

    public void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ) {
        this.contextApi.adrenaline$updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, blockX, blockY, blockZ);
    }

    public int getMinSurfaceLevel() {
        return this.contextApi.adrenaline$getMinSurfaceLevel();
    }

    public BlockState tryApply(int blockX, int blockY, int blockZ) {
        try {
            return (BlockState) this.tryApplyHandle.invokeExact(blockX, blockY, blockZ);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public BlockState tryApplySpan(int x, int y, int z, int above, int below, int water, int runBottom) {
        this.spanBottom = y;
        if (this.plan != null && runBottom < y) {
            BlockState result = this.plan.evaluate(x, y, z, above, below, water, runBottom);
            if (!this.plan.uncertain()) {
                this.spanBottom = this.plan.bottom();
                return result;
            }
        }
        return this.tryApply(x, y, z);
    }

    public int spanBottom() {
        return this.spanBottom;
    }
}
