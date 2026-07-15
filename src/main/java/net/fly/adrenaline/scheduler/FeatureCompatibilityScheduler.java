package net.fly.adrenaline.scheduler;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class FeatureCompatibilityScheduler {

    private static final Map<Long, PendingFeature> PENDING = new ConcurrentHashMap<>();

    private FeatureCompatibilityScheduler() {
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> register(
        long centerKey,
        PendingFeature pendingFeature
    ) {
        if (WorldLoadCancellation.isRequested()) {
            return CompletableFuture.completedFuture(ChunkHolder.UNLOADED_CHUNK);
        }
        PendingFeature existing = PENDING.putIfAbsent(centerKey, pendingFeature);
        if (existing == null) {
            pendingFeature.arm(centerKey);
            return pendingFeature.future();
        }

        return existing.future();
    }

    static void cancelAll() {
        for (PendingFeature pendingFeature : PENDING.values()) {
            pendingFeature.future().complete(ChunkHolder.UNLOADED_CHUNK);
        }
        PENDING.clear();
    }

    public record PendingFeature(
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future,
        List<CompletableFuture<?>> blockers,
        TryDispatch tryDispatch
    ) {
        public void arm(long centerKey) {
            if (this.blockers.isEmpty()) {
                if (PENDING.remove(centerKey, this)) {
                    this.tryDispatch.tryDispatch();
                }
                return;
            }

            AtomicInteger remaining = new AtomicInteger(this.blockers.size());
            for (CompletableFuture<?> blocker : this.blockers) {
                blocker.whenComplete((value, throwable) -> {
                    if (remaining.decrementAndGet() == 0 && PENDING.remove(centerKey, this)) {
                        this.tryDispatch.tryDispatch();
                    }
                });
            }
        }
    }

    @FunctionalInterface
    public interface TryDispatch {
        boolean tryDispatch();
    }
}
