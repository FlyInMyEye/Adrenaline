package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.biome.Biome$TemperatureModifier$2")
public interface AdrenalineMixinFrozenTemperatureAccess {
    @Invoker("modifyTemperature")
    float adrenaline$modifyFrozenTemperature(BlockPos position, float base);
}
