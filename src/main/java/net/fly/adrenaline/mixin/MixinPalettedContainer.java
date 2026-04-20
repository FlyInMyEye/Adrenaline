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
import java.util.List;
import java.util.Optional;

@Mixin(PalettedContainer.class)
public abstract class MixinPalettedContainer<T> {

    private static volatile Field DATA_FIELD;

    @Inject(method = "pack", at = @At("HEAD"), cancellable = true)
    private void fastPackSingleValue(IdMap<T> idMap, PalettedContainer.Strategy strategy,
                                     CallbackInfoReturnable<PalettedContainerRO.PackedData<T>> cir) {
        Object data = getData();
        Palette<T> palette = ((MixinPalettedContainerDataAccessor<T>) data).adrenaline$getPalette();
        if (palette.getSize() == 1) {
            cir.setReturnValue(new PalettedContainerRO.PackedData<>(List.of(palette.valueFor(0)), Optional.empty()));
        }
    }

    private Object getData() {
        try {
            Field dataField = DATA_FIELD;
            if (dataField == null) {
                dataField = findDataField();
                dataField.setAccessible(true);
                DATA_FIELD = dataField;
            }
            return dataField.get(this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Field findDataField() throws NoSuchFieldException {
        for (Field field : PalettedContainer.class.getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.world.level.chunk.PalettedContainer$Data")) {
                return field;
            }
        }
        throw new NoSuchFieldException("PalettedContainer data field");
    }
}
