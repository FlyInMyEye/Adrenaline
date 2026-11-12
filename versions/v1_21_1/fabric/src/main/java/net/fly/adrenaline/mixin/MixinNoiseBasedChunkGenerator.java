package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.GenerationJob;
import net.minecraft.Util;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseBasedChunkGenerator {

    @WrapOperation(method = "createBiomes", at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private <T> CompletableFuture<T> adrenaline$inlineBiomes(Supplier<T> work, Executor executor,
            Operation<CompletableFuture<T>> original) {
        return adrenaline$runTracked(work, executor, original, ChunkStatus.BIOMES);
    }

    @WrapOperation(method = "fillFromNoise", at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private <T> CompletableFuture<T> adrenaline$inlineNoise(Supplier<T> work, Executor executor,
            Operation<CompletableFuture<T>> original) {
        return adrenaline$runTracked(work, executor, original, ChunkStatus.NOISE);
    }

    @Unique
    private static <T> CompletableFuture<T> adrenaline$runTracked(Supplier<T> work, Executor executor,
            Operation<CompletableFuture<T>> original, ChunkStatus status) {
        Supplier<T> tracked = GenerationJob.trackExecution(work);
        if (tracked != work && AdrenalineConfig.inlineGenerationTasksEnabled()
                && AdrenalineConfig.parallelWorldgenEnabled() && AdrenalineConfig.parallelChunkStatusEnabled(status) && executor == Util.backgroundExecutor()) {
            try {
                return CompletableFuture.completedFuture(tracked.get());
            } catch (Throwable error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        return original.call(tracked, executor);
    }
}
