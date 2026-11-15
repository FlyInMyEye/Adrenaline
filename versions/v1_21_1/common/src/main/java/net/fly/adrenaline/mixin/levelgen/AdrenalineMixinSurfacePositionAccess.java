package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockPos.class)
public interface AdrenalineMixinSurfacePositionAccess {
    @Invoker("above")
    BlockPos adrenaline$temperatureAbove(int amount);

    @Invoker("below")
    BlockPos adrenaline$temperatureBelow(int amount);
}
