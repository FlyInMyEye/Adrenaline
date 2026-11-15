package net.fly.adrenaline.mixin.levelgen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.XoroshiroRandomSource$XoroshiroPositionalRandomFactory")
public interface AdrenalineMixinXoroshiroPositionalFactoryAccess {

    @Accessor("seedLo")
    long adrenaline$getSeedLo();

    @Accessor("seedHi")
    long adrenaline$getSeedHi();
}
