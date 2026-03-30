package net.fly.adrenaline.mixin;

import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    private static final ThreadLocal<ChunkHolder> CURRENT_HOLDER = new ThreadLocal<>();

    @Inject(
        method = "m_214956_",
        remap = false,
        at = @At("HEAD")
    )
    private void captureHolder(ChunkHolder holder, Runnable task, CallbackInfo ci) {
        CURRENT_HOLDER.set(holder);
    }

    @Redirect(
        method = "m_214956_",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/thread/ProcessorHandle;m_6937_(Ljava/lang/Object;)V",
            remap = false
        )
    )
    private void redirectWorldgenDispatch(
        ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> instance,
        Object message
    ) {
        MixinMessageAccessor accessor = (MixinMessageAccessor) (Object) message;
        ChunkPos pos = new ChunkPos(accessor.getPos());

        ChunkHolder holder = CURRENT_HOLDER.get();

        int writeRadius = (holder != null && isFeatureStage(holder)) ? 1 : 0;

        ChunkJobScheduler.get().submit(new ChunkJob(pos, writeRadius, () -> {
            @SuppressWarnings("unchecked")
            Function<ProcessorHandle<Unit>, Runnable> taskFunc =
                (Function<ProcessorHandle<Unit>, Runnable>) accessor.getTask();
            ProcessorHandle<Unit> dummy = ProcessorHandle.of("adrenaline-wrap", unit -> {});
            Runnable task = taskFunc.apply(dummy);
            task.run();
        }));
    }

    private static boolean isFeatureStage(ChunkHolder holder) {
        ChunkStatus last = holder.getLastAvailableStatus();
        if (last == null) {
            return false;
        }
        List<ChunkStatus> statuses = ChunkStatus.getStatusList();
        int next = last.getIndex() + 1;
        if (next >= statuses.size()) {
            return false;
        }
        return statuses.get(next) == ChunkStatus.FEATURES;
    }
}
