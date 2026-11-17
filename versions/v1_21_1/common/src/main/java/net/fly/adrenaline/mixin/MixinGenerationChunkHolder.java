package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GenerationChunkHolder.class)
public abstract class MixinGenerationChunkHolder {

    @Shadow
    protected abstract void failAndClearPendingFuturesBetween(ChunkStatus from, ChunkStatus to);

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;handle(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private CompletableFuture<ChunkResult<ChunkAccess>> adrenaline$completeCancelledStep(CompletableFuture<ChunkAccess> future,
            BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> completion,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        return original.call(future, (BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>>) (chunk, error) -> {
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof ChunkJobScheduler.CancelledStepException) {
                this.failAndClearPendingFuturesBetween(null, ChunkStatus.FULL);
                return GenerationChunkHolder.UNLOADED_CHUNK;
            }
            return completion.apply(chunk, error);
        });
    }
}
