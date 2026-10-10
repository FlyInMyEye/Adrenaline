package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineReciprocalFunctionAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.worldgen.tectonic.worldgen.densityfunction.Invert", remap = false)
public interface MixinTectonicInvert extends AdrenalineReciprocalFunctionAccess {
    @Accessor("input") DensityFunction adrenaline$getReciprocalInput();
}
