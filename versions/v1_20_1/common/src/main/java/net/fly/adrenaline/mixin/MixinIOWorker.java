package net.fly.adrenaline.mixin;

import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.util.ChunkSaveQueue;
import net.fly.adrenaline.util.SaveBackpressure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IOWorker.class)
public class MixinIOWorker implements SaveBackpressure {
    @Unique
    private ChunkSaveQueue adrenaline$saveQueue;

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
