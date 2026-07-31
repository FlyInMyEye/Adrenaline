package net.fly.adrenaline.mixin;

import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkTaskPriorityQueueSorter.class)
public abstract class MixinChunkTaskPriorityQueueSorter {

    @Shadow
    private <T> void submit(ProcessorHandle<T> processor, Function<ProcessorHandle<Unit>, T> task, long pos, IntSupplier level, boolean addBlocker) {
        throw new AssertionError();
    }

    @Inject(method = "getProcessor", at = @At("HEAD"), cancellable = true)
    private <T> void adrenaline$createProcessorImmediately(ProcessorHandle<T> processor, boolean addBlocker, CallbackInfoReturnable<ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<T>>> cir) {
        cir.setReturnValue(ProcessorHandle.of("chunk priority sorter around " + processor.name(), message -> {
            MixinMessageAccessor accessor = (MixinMessageAccessor) (Object) message;
            @SuppressWarnings("unchecked")
            Function<ProcessorHandle<Unit>, T> task = (Function<ProcessorHandle<Unit>, T>) accessor.getTask();
            this.submit(processor, task, accessor.getPos(), accessor.getLevel(), addBlocker);
        }));
    }
}
