package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({Holder.Reference.class, Holder.Direct.class})
public abstract class AdrenalineMixinSurfaceHolders {
}
