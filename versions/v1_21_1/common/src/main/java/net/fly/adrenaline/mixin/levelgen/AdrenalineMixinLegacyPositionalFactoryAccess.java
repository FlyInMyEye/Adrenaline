package net.fly.adrenaline.mixin.levelgen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.LegacyRandomSource$LegacyPositionalRandomFactory")
public interface AdrenalineMixinLegacyPositionalFactoryAccess {

    @Accessor("seed")
    long adrenaline$getSeed();
}
