package net.fly.adrenaline.mixin;

import com.mojang.datafixers.util.Either;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.fly.adrenaline.util.ChunkSaveQueue;
import net.fly.adrenaline.util.SaveBackpressure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IOWorker.class)
public class MixinIOWorker implements SaveBackpressure {
    @Shadow @Final
    private static Logger LOGGER;
    @Shadow @Final
    private RegionFileStorage storage;
    @Shadow @Final
    private Map<ChunkPos, ?> pendingWrites;

    @Unique
    private ChunkSaveQueue adrenaline$saveQueue;

    @Shadow
    private <T> CompletableFuture<T> submitTask(Supplier<Either<T, Exception>> task) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isOldChunk(CompoundTag data) {
        throw new AssertionError();
    }

    @Inject(method = "createOldDataForRegion", at = @At("HEAD"), cancellable = true)
    private void adrenaline$batchBlendingScan(int regionX, int regionZ, CallbackInfoReturnable<CompletableFuture<BitSet>> cir) {
        if (((Object) this).getClass() != IOWorker.class) {
            return;
        }
        cir.setReturnValue(this.submitTask(() -> {
            BitSet oldChunks = new BitSet(1024);
            RegionFile region;
            try {
                region = ((MixinRegionFileStorageInvoker) (Object) this.storage).adrenaline$getRegionFile(ChunkPos.minFromRegion(regionX, regionZ));
            } catch (Exception exception) {
                region = null;
            }
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    ChunkPos pos = new ChunkPos((regionX << 5) + x, (regionZ << 5) + z);
                    Object pending = this.pendingWrites.get(pos);
                    if (pending == null && region != null && !region.hasChunk(pos)) {
                        continue;
                    }
                    CollectFields fields = new CollectFields(
                        new FieldSelector(IntTag.TYPE, "DataVersion"),
                        new FieldSelector(CompoundTag.TYPE, "blending_data")
                    );
                    try {
                        if (pending != null) {
                            CompoundTag data = ((MixinIOWorkerPendingStoreAccessor) pending).adrenaline$getData();
                            if (data != null) {
                                data.acceptAsRoot(fields);
                            }
                        } else {
                            this.storage.scanChunk(pos, fields);
                        }
                        if (fields.getResult() instanceof CompoundTag data && this.isOldChunk(data)) {
                            oldChunks.set(z * 32 + x);
                        }
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to scan chunk {}", pos, exception);
                    }
                }
            }
            return Either.left(oldChunks);
        }));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializeBackpressure(CallbackInfo ci) {
        this.adrenaline$saveQueue = new ChunkSaveQueue();
    }

    @ModifyArg(
        method = "tellStorePending",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/StrictQueue$IntRunnable;<init>(ILjava/lang/Runnable;)V"),
        index = 0
    )
    private int adrenaline$prioritizeBackloggedStores(int priority) {
        return this.adrenaline$saveQueue.hasCapacity() ? priority : 0;
    }

    @Inject(method = "store", at = @At("RETURN"), cancellable = true)
    private void adrenaline$trackStore(ChunkPos pos, CompoundTag data, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> store = cir.getReturnValue();
        this.adrenaline$saveQueue.track(store);
        cir.setReturnValue(store.thenApply(unused -> null));
    }

    @Override
    public boolean adrenaline$hasSaveCapacity() {
        return this.adrenaline$saveQueue.hasCapacity();
    }

    @Override
    public CompletableFuture<Void> adrenaline$awaitSaveCapacity() {
        return this.adrenaline$saveQueue.awaitCapacity();
    }
}
