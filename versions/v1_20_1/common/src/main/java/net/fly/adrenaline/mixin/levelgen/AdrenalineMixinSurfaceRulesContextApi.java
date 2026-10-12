package net.fly.adrenaline.mixin.levelgen;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public interface AdrenalineMixinSurfaceRulesContextApi {

    @Invoker("updateXZ")
    void adrenaline$updateXZ(int blockX, int blockZ);

    @Invoker("updateY")
    void adrenaline$updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ);

    @Invoker("getMinSurfaceLevel")
    int adrenaline$getMinSurfaceLevel();

    @Accessor("surfaceDepth")
    int adrenaline$getSurfaceDepth();

    @Invoker("getSurfaceSecondary")
    double adrenaline$getSurfaceSecondary();

    @Accessor("biome")
    Supplier<Holder<Biome>> adrenaline$getBiome();

    @Accessor("stoneDepthAbove")
    int adrenaline$getStoneDepthAbove();

    @Accessor("stoneDepthBelow")
    int adrenaline$getStoneDepthBelow();

    @Accessor("waterHeight")
    int adrenaline$getWaterHeight();
}
