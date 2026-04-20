package net.fly.adrenaline.mixin;

import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer$Data")
public interface MixinPalettedContainerDataAccessor<T> {

    @Invoker("palette")
    Palette<T> adrenaline$getPalette();
}
