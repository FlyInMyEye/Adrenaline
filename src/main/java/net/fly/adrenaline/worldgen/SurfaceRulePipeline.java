package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.minecraft.world.level.block.state.BlockState;

public final class SurfaceRulePipeline {

    private final Object surfaceRule;
    private final AdrenalineMixinSurfaceRulesContextApi contextApi;

    public SurfaceRulePipeline(Object surfaceRule, Object context) {
        this.surfaceRule = surfaceRule;
        this.contextApi = (AdrenalineMixinSurfaceRulesContextApi) context;
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
        return SurfaceRulesContextFactory.tryApply(this.surfaceRule, blockX, blockY, blockZ);
    }
}
