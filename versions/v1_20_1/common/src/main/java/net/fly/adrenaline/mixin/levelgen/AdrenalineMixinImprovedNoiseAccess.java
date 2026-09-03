package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ImprovedNoise.class)
public interface AdrenalineMixinImprovedNoiseAccess {

    @Accessor("p")
    byte[] adrenaline$getPermutation();

    @Accessor("xo")
    double adrenaline$getXo();

    @Accessor("yo")
    double adrenaline$getYo();

    @Accessor("zo")
    double adrenaline$getZo();
}
