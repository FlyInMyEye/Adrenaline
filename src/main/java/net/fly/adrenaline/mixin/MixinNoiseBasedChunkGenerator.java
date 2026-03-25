package net.fly.adrenaline.mixin;

import net.minecraft.Util;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {

    @Redirect(
        method = "createBiomes",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> inlineCreateBiomes(Supplier<T> supplier, Executor executor) {
        if (executor == Util.backgroundExecutor()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> inlineFillFromNoise(Supplier<T> supplier, Executor executor) {
        if (executor == Util.backgroundExecutor()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
