package net.fly.adrenaline.scheduler;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class ChunkGenerationRequest {
    private final ChunkStatus status;
    private final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completion = new CompletableFuture<>();

    public ChunkGenerationRequest(ChunkStatus status) {
        this.status = status;
    }

    public ChunkStatus status() {
        return this.status;
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completion() {
        return this.completion;
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> bind(
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generation
    ) {
        generation.whenComplete((chunk, failure) -> {
            if (failure == null) {
                this.completion.complete(chunk);
            } else {
                this.completion.completeExceptionally(failure);
            }
        });
        return this.completion;
    }

    public void cancel() {
        this.completion.complete(ChunkHolder.UNLOADED_CHUNK);
    }
}
