package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Vec3i.class)
public interface AdrenalineMixinSurfaceVectorAccess {
    @Invoker("getX")
    int adrenaline$temperatureX();

    @Invoker("getY")
    int adrenaline$temperatureY();

    @Invoker("getZ")
    int adrenaline$temperatureZ();
}
