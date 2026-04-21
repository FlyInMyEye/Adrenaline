package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface AdrenalineMixinNoiseChunkNoiseInterpolatorInvoker {

    @Invoker("selectCellYZ")
    void adrenaline$selectCellYZ(int cellY, int cellZ);

    @Invoker("updateForY")
    void adrenaline$updateForY(double yLerp);

    @Invoker("updateForX")
    void adrenaline$updateForX(double xLerp);

    @Invoker("updateForZ")
    void adrenaline$updateForZ(double zLerp);
}
