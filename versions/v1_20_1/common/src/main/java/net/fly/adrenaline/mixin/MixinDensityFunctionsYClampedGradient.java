package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineYGradientAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$YClampedGradient")
public interface MixinDensityFunctionsYClampedGradient extends AdrenalineYGradientAccess {
    @Accessor("fromY") int adrenaline$getFromY();
    @Accessor("toY") int adrenaline$getToY();
    @Accessor("fromValue") double adrenaline$getFromValue();
    @Accessor("toValue") double adrenaline$getToValue();
}
