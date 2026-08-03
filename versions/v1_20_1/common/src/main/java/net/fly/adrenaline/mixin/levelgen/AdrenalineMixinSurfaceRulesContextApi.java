package net.fly.adrenaline.mixin.levelgen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public interface AdrenalineMixinSurfaceRulesContextApi {

    @Invoker("updateXZ")
    void adrenaline$updateXZ(int blockX, int blockZ);

    @Invoker("updateY")
    void adrenaline$updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ);

    @Invoker("getMinSurfaceLevel")
    int adrenaline$getMinSurfaceLevel();
}
