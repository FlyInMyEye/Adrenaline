package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.io.ChunkStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(IOWorker.class)
public class MixinIOWorker {

    private static final ThreadLocal<Object> CAPTURED_PENDING_STORE = new ThreadLocal<>();

    @Redirect(
        method = "storePendingChunk",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Iterator;next()Ljava/lang/Object;"
        )
    )
    private <T> T captureEntry(Iterator<T> iterator) {
        T entry = iterator.next();
        if (entry instanceof Map.Entry) {
            CAPTURED_PENDING_STORE.set(((Map.Entry<?, ?>) entry).getValue());
        }
        return entry;
    }

    @Inject(method = "runStore", at = @At("HEAD"))
    private void clearPendingStoreBeforeRun(CallbackInfo ci) {
        CAPTURED_PENDING_STORE.remove();
    }

    @Redirect(
        method = "runStore",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"
        )
    )
    private void redirectToDataFly(RegionFileStorage storage, ChunkPos pos, CompoundTag data) {
        if (!AdrenalineConfig.get().chunkIoCache) {
            ((MixinRegionFileStorageInvoker) (Object) storage).adrenaline$write(pos, data);
            return;
        }

        if (data != null) {
            CompletableFuture<?> dataFlyFuture = ChunkStore.write(pos, data, ((MixinRegionFileStorageAccessor) (Object) storage).adrenaline$getFolder()).toFuture();
            Object pendingStore = CAPTURED_PENDING_STORE.get();
            if (pendingStore != null) {
                CompletableFuture<Void> result = ((MixinIOWorkerPendingStoreAccessor) pendingStore).adrenaline$getResult();
                dataFlyFuture.whenComplete((ignored, exception) -> {
                    if (exception != null) {
                        result.completeExceptionally(exception);
                    } else {
                        result.complete(null);
                    }
                });
                CAPTURED_PENDING_STORE.remove();
                return;
            }
        }

        ((MixinRegionFileStorageInvoker) (Object) storage).adrenaline$write(pos, data);
    }

    @Inject(method = "runStore", at = @At("RETURN"))
    private void clearPendingStoreAfterRun(CallbackInfo ci) {
        CAPTURED_PENDING_STORE.remove();
    }
}
