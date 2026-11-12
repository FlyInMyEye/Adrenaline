package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStatusTasks.class)
public abstract class MixinChunkStatusTasks {

    @WrapOperation(method = "full", at = @At(value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private static CompletableFuture<ChunkAccess> adrenaline$timeFullConversion(Supplier<ChunkAccess> work, Executor executor,
            Operation<CompletableFuture<ChunkAccess>> original, WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        return WorldgenStageStats.trackFullConversion(chunk.getPos(), ignored -> work.get(), executor,
            (conversion, dispatch) -> original.call((Supplier<ChunkAccess>) () -> conversion.apply(null), dispatch));
    }
}
