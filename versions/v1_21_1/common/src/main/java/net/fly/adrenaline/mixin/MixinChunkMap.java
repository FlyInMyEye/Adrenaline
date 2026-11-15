package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import net.fly.adrenaline.util.SaveBackpressure;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldgenWarmup;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow @Final private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    @Shadow @Final private ChunkGeneratorStructureState chunkGeneratorState;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final private ServerLevel level;
    @Unique private int adrenaline$saveFlushDepth;
    @Unique private int adrenaline$processedUnloads;

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkAccess> adrenaline$waitForSaveCapacity(GenerationChunkHolder holder, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, Operation<CompletableFuture<ChunkAccess>> original) {
        if (step.targetStatus() != ChunkStatus.FULL) {
            return original.call(holder, step, cache);
        }
        CompletableFuture<Void> capacity = this.adrenaline$saveBackpressure().adrenaline$awaitSaveCapacity();
        if (capacity.isDone()) {
            return original.call(holder, step, cache);
        }
        return capacity.thenComposeAsync(ignored -> original.call(holder, step, cache), this.level.getServer());
    }

    @WrapMethod(method = "saveAllChunks")
    private void adrenaline$flushSaveQueue(boolean flush, Operation<Void> original) {
        if (flush) {
            this.adrenaline$saveFlushDepth++;
        }
        try {
            original.call(flush);
        } finally {
            if (flush) {
                this.adrenaline$saveFlushDepth--;
            }
        }
    }

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void adrenaline$resetUnloadBudget(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        this.adrenaline$processedUnloads = 0;
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object adrenaline$pollUnloadWithSaveCapacity(Queue<Runnable> queue, BooleanSupplier shouldKeepTicking) {
        SaveBackpressure backpressure = this.adrenaline$saveBackpressure();
        if (this.adrenaline$saveFlushDepth > 0) {
            backpressure.adrenaline$awaitSaveCapacity().join();
        } else if (!backpressure.adrenaline$hasSaveCapacity()
            || this.adrenaline$processedUnloads > 0 && !shouldKeepTicking.getAsBoolean()) {
            return null;
        }
        Runnable task = queue.poll();
        if (task != null) {
            this.adrenaline$processedUnloads++;
        }
        return task;
    }

    @Inject(method = "saveChunkIfNeeded", at = @At("HEAD"), cancellable = true)
    private void adrenaline$deferAutosaveWhenFull(ChunkHolder holder, CallbackInfoReturnable<Boolean> cir) {
        if (this.adrenaline$saveFlushDepth == 0 && !this.adrenaline$saveBackpressure().adrenaline$hasSaveCapacity()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void adrenaline$boundFlushSerialization(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (this.adrenaline$saveFlushDepth > 0 && chunk.isUnsaved()) {
            this.adrenaline$saveBackpressure().adrenaline$awaitSaveCapacity().join();
        }
    }

    @Unique
    private SaveBackpressure adrenaline$saveBackpressure() {
        return (SaveBackpressure) ((MixinChunkStorageAccessor) (Object) this).adrenaline$getWorker();
    }

    @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipObsoleteUnload(long pos, ChunkHolder holder, CallbackInfo ci) {
        if (this.pendingUnloads.get(pos) != holder) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkAccess> adrenaline$measureScheduling(GenerationChunkHolder holder, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, Operation<CompletableFuture<ChunkAccess>> original) {
        if (!BuildConfig.DEBUG || step.targetStatus() == ChunkStatus.EMPTY
                || !holder.getPersistedStatus().isBefore(step.targetStatus())) {
            return original.call(holder, step, cache);
        }
        WorldgenStageStats.beginScheduling(holder.getPos(), step.targetStatus());
        WorldgenStageStats.markDependenciesReady(holder.getPos(), step.targetStatus());
        try {
            return original.call(holder, step, cache);
        } finally {
            WorldgenStageStats.finishInitialScheduling(holder.getPos(), step.targetStatus());
        }
    }

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/status/ChunkStep;apply(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"))
    @ControlsOptimization(Optimization.GENERATION_DISPATCH)
    private CompletableFuture<ChunkAccess> adrenaline$scheduleStep(ChunkStep step, WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original,
            GenerationChunkHolder holder, ChunkStep requestedStep, StaticCache2D<GenerationChunkHolder> requestedCache) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.GENERATION_DISPATCH)
                || !ChunkJobScheduler.usesScheduler(context.level()) || step.targetStatus() == ChunkStatus.FULL
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
