package net.fly.adrenaline.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Aquifer.FluidStatus.class)
public interface MixinAquiferFluidStatusAccessor {

    @Accessor("fluidLevel")
    int adrenaline$fluidLevel();
}
