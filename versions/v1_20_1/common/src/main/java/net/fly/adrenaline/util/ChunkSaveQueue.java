package net.fly.adrenaline.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkSaveQueue {
    private static final int MAX_OUTSTANDING_STORES = 1024;

    private final AtomicInteger outstandingStores = new AtomicInteger();
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> waitingGeneration = new ConcurrentLinkedQueue<>();

    public boolean hasCapacity() {
        return this.outstandingStores.get() < MAX_OUTSTANDING_STORES;
    }

    public void track(CompletableFuture<?> store) {
        this.outstandingStores.incrementAndGet();
        store.whenComplete((unused, failure) -> {
            int outstanding = this.outstandingStores.decrementAndGet();
            if (outstanding < MAX_OUTSTANDING_STORES) {
                CompletableFuture<Void> waiting = this.waitingGeneration.poll();
                if (waiting != null) {
                    waiting.complete(null);
                }
            }
            if (outstanding == 0) {
                CompletableFuture<Void> waiting;
                while ((waiting = this.waitingGeneration.poll()) != null) {
                    waiting.complete(null);
                }
            }
        });
    }

    public CompletableFuture<Void> awaitCapacity() {
        if (this.hasCapacity()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> waiting = new CompletableFuture<>();
        this.waitingGeneration.add(waiting);
        if (this.hasCapacity() && this.waitingGeneration.remove(waiting)) {
            waiting.complete(null);
        }
        return waiting;
    }
}
