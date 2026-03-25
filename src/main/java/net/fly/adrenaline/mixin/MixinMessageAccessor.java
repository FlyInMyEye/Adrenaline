package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(ChunkTaskPriorityQueueSorter.Message.class)
public interface MixinMessageAccessor {

    @Accessor("task")
    Function<ProcessorHandle<Unit>, ?> getTask();

    @Accessor("pos")
    long getPos();
}
