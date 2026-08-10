package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineMappedFunctionAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Mapped")
public abstract class MixinDensityFunctionsMapped implements AdrenalineMappedFunctionAccess {

    @Shadow
    public abstract DensityFunction input();

    @Override
    public DensityFunction adrenaline$mappedInput() {
        return this.input();
    }
}
