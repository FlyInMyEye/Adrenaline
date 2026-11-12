package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldgenWarmup;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow @Final private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    @Shadow @Final private ChunkGeneratorStructureState chunkGeneratorState;

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/status/ChunkStep;apply(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"))
    @ControlsOptimization(Optimization.GENERATION_DISPATCH)
    private CompletableFuture<ChunkAccess> adrenaline$scheduleStep(ChunkStep step, WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original,
            GenerationChunkHolder holder, ChunkStep requestedStep, StaticCache2D<GenerationChunkHolder> requestedCache) {
        if (!AdrenalineConfig.parallelWorldgenEnabled() || step.targetStatus() == ChunkStatus.FULL
                || chunk.getPersistedStatus().isOrAfter(step.targetStatus())) {
            return original.call(step, context, cache, chunk);
        }
        this.chunkGeneratorState.ensureStructuresGenerated();
        WorldgenWarmup.warmSharedCaches();
        return ChunkJobScheduler.get().submitStep(context.level(), step, chunk,
            () -> original.call(step, context, cache, chunk),
            task -> this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, task)));
    }
}
