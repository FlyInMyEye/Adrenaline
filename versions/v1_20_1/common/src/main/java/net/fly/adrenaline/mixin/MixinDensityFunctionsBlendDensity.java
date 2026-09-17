package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineBlendDensityAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$BlendDensity")
public abstract class MixinDensityFunctionsBlendDensity implements AdrenalineBlendDensityAccess {

    @Shadow
    public abstract DensityFunction input();

    @Override
    public DensityFunction adrenaline$getBlendDensityInput() {
        return this.input();
    }
}
