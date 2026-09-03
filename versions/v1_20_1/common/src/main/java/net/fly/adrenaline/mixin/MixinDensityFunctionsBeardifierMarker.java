package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineBeardifierMarkerAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$BeardifierMarker")
public abstract class MixinDensityFunctionsBeardifierMarker implements AdrenalineBeardifierMarkerAccess {
}
