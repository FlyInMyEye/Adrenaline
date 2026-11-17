package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.util.GenerationRequestTiming;
import net.minecraft.server.level.ChunkGenerationTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkGenerationTask.class)
public class MixinChunkGenerationTaskTiming {
    @Unique
    private final long adrenaline$requestedNanos = System.nanoTime();

    @WrapMethod(method = "runUntilWait")
    private CompletableFuture<?> adrenaline$trackDependencyWait(Operation<CompletableFuture<?>> original) {
        Long previous = GenerationRequestTiming.enter(this.adrenaline$requestedNanos);
        try {
            return original.call();
        } finally {
            GenerationRequestTiming.restore(previous);
        }
    }
}
