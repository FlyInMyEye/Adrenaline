package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fly.adrenaline.util.DebugChunkGeneration;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGeneratorDebug {
    @WrapOperation(method = {"createBiomes", "fillFromNoise"}, at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private <T> CompletableFuture<T> adrenaline$keepDebugGenerationScoped(Supplier<T> work, Executor executor,
            Operation<CompletableFuture<T>> original) {
        return original.call(work, DebugChunkGeneration.isActive() ? (Executor) Runnable::run : executor);
    }
}
