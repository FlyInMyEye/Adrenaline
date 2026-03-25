package net.fly.adrenaline.mixin;

import net.fly.adrenaline.scheduler.ChunkWorkerPool;
import net.fly.adrenaline.scheduler.GenerationTask;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.thread.ProcessorHandle;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    @Shadow
    private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;

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
        int writeRadius = 1;

        ChunkWorkerPool.submit(new GenerationTask(pos, writeRadius, () -> {
            @SuppressWarnings("unchecked")
            Function<ProcessorHandle<Unit>, Runnable> taskFunc =
                (Function<ProcessorHandle<Unit>, Runnable>) accessor.getTask();
            ProcessorHandle<Unit> dummy = ProcessorHandle.of("adrenaline-wrap", unit -> {
            });
            Runnable task = taskFunc.apply(dummy);
            task.run();
        }));
    }
}
