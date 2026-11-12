package net.fly.adrenaline.util;

import java.util.concurrent.CompletableFuture;

public interface SaveBackpressure {
    boolean adrenaline$hasSaveCapacity();

    CompletableFuture<Void> adrenaline$awaitSaveCapacity();
}
