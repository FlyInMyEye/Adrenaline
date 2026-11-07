package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.PaletteResizeCopy;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer$Data")
public class MixinPalettedContainerData<T> {

    @Shadow @Final private BitStorage storage;
    @Shadow @Final private Palette<T> palette;

    @Inject(method = "copyFrom", at = @At("HEAD"), cancellable = true, require = 0)
    @ControlsOptimization(Optimization.PALETTE_RESIZE)
    private void adrenaline$copyPalette(Palette<T> sourcePalette, BitStorage source, CallbackInfo ci) {
        if (AdrenalineConfig.paletteResizeOptimizationsEnabled() && PaletteResizeCopy.copy(sourcePalette, source, this.palette, this.storage)) {
            ci.cancel();
        }
    }
}
