package net.fly.adrenaline.mixin;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.fly.adrenaline.scheduler.PendingChunkStatusAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public class MixinChunkHolderPendingStatus implements PendingChunkStatusAccess {

    @Unique
    private Deque<ChunkStatus> adrenaline$pendingStatuses;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializePendingStatuses(CallbackInfo ci) {
        this.adrenaline$pendingStatuses = new ConcurrentLinkedDeque<>();
    }

    @Override
    public void adrenaline$enqueuePendingStatus(ChunkStatus status) {
        this.adrenaline$pendingStatuses.addLast(status);
    }

    @Override
    public ChunkStatus adrenaline$pollPendingStatus() {
        return this.adrenaline$pendingStatuses.pollFirst();
    }
}
