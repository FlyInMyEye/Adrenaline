package net.fly.adrenaline.mixin;

import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.core.IdMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@Mixin(PalettedContainer.class)
public abstract class MixinPalettedContainer<T> {

    private static volatile Field DATA_FIELD;
    private static volatile Method DATA_PALETTE_METHOD;

    @Inject(method = "pack", at = @At("HEAD"), cancellable = true)
    private void fastPackSingleValue(IdMap<T> idMap, PalettedContainer.Strategy strategy,
                                     CallbackInfoReturnable<PalettedContainerRO.PackedData<T>> cir) {
        try {
            Field dataField = DATA_FIELD;
            if (dataField == null) {
                dataField = PalettedContainer.class.getDeclaredField("data");
                dataField.setAccessible(true);
                DATA_FIELD = dataField;
            }
            Object data = dataField.get(this);

            Method paletteMethod = DATA_PALETTE_METHOD;
            if (paletteMethod == null) {
                paletteMethod = data.getClass().getMethod("palette");
                DATA_PALETTE_METHOD = paletteMethod;
            }
            @SuppressWarnings("unchecked")
            Palette<T> palette = (Palette<T>) paletteMethod.invoke(data);

            if (palette.getSize() == 1) {
                cir.setReturnValue(new PalettedContainerRO.PackedData<>(List.of(palette.valueFor(0)), Optional.empty()));
            }
        } catch (Exception ignored) {
        }
    }
}
