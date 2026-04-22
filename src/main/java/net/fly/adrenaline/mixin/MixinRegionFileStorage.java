package net.fly.adrenaline.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegionFileStorage.class)
public class MixinRegionFileStorage {

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void readFromDataFly(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
    }
}
