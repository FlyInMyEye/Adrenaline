package net.fly.adrenaline.mixin;

import java.util.function.Function;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkTaskPriorityQueueSorter.Message.class)
public interface MixinMessageAccessor {

    @Accessor("task")
    Function<ProcessorHandle<Unit>, ?> getTask();

    @Mutable
    @Accessor("task")
    void setTask(Function<ProcessorHandle<Unit>, ?> task);

    @Accessor("pos")
    long getPos();
}
