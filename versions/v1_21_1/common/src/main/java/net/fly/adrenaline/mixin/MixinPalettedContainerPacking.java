package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;

import net.fly.adrenaline.worldgen.SectionPaletteBuilder;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PalettedContainer.class)
public abstract class MixinPalettedContainerPacking<T> {
    @Inject(method = "pack", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.PALETTE_PACKING)
    private void adrenaline$packPalette(IdMap<T> registry, PalettedContainer.Strategy strategy, CallbackInfoReturnable<PalettedContainerRO.PackedData<T>> cir) {
        if (!AdrenalineConfig.palettePackingOptimizationsEnabled()) {
            return;
        }
        @SuppressWarnings("unchecked")
        PalettedContainer<T> source = (PalettedContainer<T>) (Object) this;
        PalettedContainerRO.PackedData<T> packed = SectionPaletteBuilder.pack(source, registry, strategy);
        if (packed != null) {
            cir.setReturnValue(packed);
        }
    }
}
