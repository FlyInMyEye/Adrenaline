package net.fly.adrenaline.mixin;

import net.fly.adrenaline.io.ChunkStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
public class MixinRegionFileStorage {

    private static Field FOLDER_FIELD;

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void readFromDataFly(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
        try {
            if (FOLDER_FIELD == null) {
                FOLDER_FIELD = RegionFileStorage.class.getDeclaredField("folder");
                FOLDER_FIELD.setAccessible(true);
            }
            Path folder = (Path) FOLDER_FIELD.get(this);
            CompoundTag data = ChunkStore.read(pos, folder);
            if (data != null) {
                cir.setReturnValue(data);
            }
        } catch (Exception e) {
        }
    }
}
