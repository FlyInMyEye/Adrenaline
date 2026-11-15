package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimplexNoise.class)
public interface AdrenalineMixinSimplexNoiseAccess {
    @Accessor("p")
    int[] adrenaline$getPermutation();
}
