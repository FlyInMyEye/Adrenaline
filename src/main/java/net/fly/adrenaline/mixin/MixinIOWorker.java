package net.fly.adrenaline.mixin;

import net.fly.adrenaline.io.ChunkStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(IOWorker.class)
public class MixinIOWorker {

    private static Field FOLDER_FIELD;
    private static Field RESULT_FIELD;
    private static final ThreadLocal<Map.Entry<?, ?>> CAPTURED_ENTRY = new ThreadLocal<>();

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
            CAPTURED_ENTRY.set((Map.Entry<?, ?>) entry);
        }
        return entry;
    }

    @Redirect(
        method = "runStore",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"
        )
    )
    private void redirectToDataFly(RegionFileStorage storage, ChunkPos pos, CompoundTag data) {
        if (data != null) {
            try {
                if (FOLDER_FIELD == null) {
                    FOLDER_FIELD = RegionFileStorage.class.getDeclaredField("folder");
                    FOLDER_FIELD.setAccessible(true);
                }
                Path folder = (Path) FOLDER_FIELD.get(storage);
                CompletableFuture<?> dfFuture = ChunkStore.write(pos, data, folder).toFuture();

                Map.Entry<?, ?> entry = CAPTURED_ENTRY.get();
                if (entry != null) {
                    if (RESULT_FIELD == null) {
                        RESULT_FIELD = entry.getValue().getClass().getDeclaredField("result");
                        RESULT_FIELD.setAccessible(true);
                    }
                    CompletableFuture<Void> result = (CompletableFuture<Void>) RESULT_FIELD.get(entry.getValue());
                    dfFuture.whenComplete((r, ex) -> {
                        if (ex != null) {
                            result.completeExceptionally(ex);
                        } else {
                            result.complete(null);
                        }
                    });
                    CAPTURED_ENTRY.remove();
                    return;
                }
            } catch (Exception e) {
            }
        }
    }
}
