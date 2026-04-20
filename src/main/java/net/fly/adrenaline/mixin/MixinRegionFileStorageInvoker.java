package net.fly.adrenaline.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RegionFileStorage.class)
public interface MixinRegionFileStorageInvoker {

    @Invoker("write")
    void adrenaline$write(ChunkPos pos, CompoundTag tag);
}
