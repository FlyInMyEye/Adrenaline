package net.fly.adrenaline.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore")
public interface MixinIOWorkerPendingStoreAccessor {

    @Accessor("result")
    CompletableFuture<Void> adrenaline$getResult();
}
