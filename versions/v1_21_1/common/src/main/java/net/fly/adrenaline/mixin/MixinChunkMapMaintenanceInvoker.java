package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface MixinChunkMapMaintenanceInvoker {
    @Invoker("tick")
    void adrenaline$tickStorage(BooleanSupplier shouldKeepTicking);
}
