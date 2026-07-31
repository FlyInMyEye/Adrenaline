package net.fly.adrenaline.mixin;

import com.mojang.datafixers.util.Either;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldgenPreparation;
import net.fly.adrenaline.util.WorldgenWarmup;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.SchedulingWork;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    @Unique
    private ThreadLocal<ChunkHolder> adrenaline$currentHolder;

    @Unique
    private Map<ChunkHolder, Deque<ChunkStatus>> adrenaline$pendingStatus;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializeSchedulingState(CallbackInfo ci) {
        this.adrenaline$currentHolder = new ThreadLocal<>();
        this.adrenaline$pendingStatus = new ConcurrentHashMap<>();
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createState(Lnet/minecraft/core/HolderLookup;Lnet/minecraft/world/level/levelgen/RandomState;J)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"
        )
    )
    private ChunkGeneratorStructureState adrenaline$usePreparedStructureState(ChunkGenerator generator, HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        ChunkGeneratorStructureState prepared = WorldgenPreparation.take(generator, seed);
        return prepared == null ? generator.createState(structureSets, randomState, seed) : prepared;
    }

    @Inject(
        method = "scheduleChunkGeneration",
        at = @At("HEAD")
    )
    private void captureStatus(ChunkHolder holder, ChunkStatus status, CallbackInfoReturnable<CompletableFuture<?>> cir) {
        this.adrenaline$pendingStatus.computeIfAbsent(holder, ignored -> new ConcurrentLinkedDeque<>()).addLast(status);
        ChunkJobScheduler.get().dependencyScheduled();
        if (BuildConfig.DEBUG) {
            WorldgenStageStats.beginScheduling(holder.getPos(), status);
        }
    }

    @Inject(
        method = "scheduleChunkGeneration",
        at = @At("RETURN")
    )
    private void finishInitialScheduling(ChunkHolder holder, ChunkStatus status, CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (BuildConfig.DEBUG) {
            WorldgenStageStats.finishInitialScheduling(holder.getPos(), status);
        }
    }

    @Inject(
        method = "m_214956_",
        remap = false,
        at = @At("HEAD")
    )
    private void captureHolder(ChunkHolder holder, Runnable task, CallbackInfo ci) {
        this.adrenaline$currentHolder.set(holder);
    }

    @Redirect(
        method = "m_214956_",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/thread/ProcessorHandle;m_6937_(Ljava/lang/Object;)V",
            remap = false
        )
    )
    private void redirectWorldgenDispatch(ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> instance, Object message) {
        ChunkHolder holder = this.adrenaline$currentHolder.get();
        ChunkStatus nextStatus = null;
        if (holder != null) {
            Deque<ChunkStatus> pending = this.adrenaline$pendingStatus.get(holder);
            if (pending != null) {
                nextStatus = pending.pollFirst();
                if (nextStatus != null) {
                    ChunkJobScheduler.get().dependencyReady();
                }
                if (pending.isEmpty()) {
                    this.adrenaline$pendingStatus.remove(holder, pending);
                }
            }
        }

        MixinMessageAccessor accessor = (MixinMessageAccessor) (Object) message;
        ChunkPos pos = new ChunkPos(accessor.getPos());
        SchedulingWork dispatchWork = BuildConfig.DEBUG && nextStatus != null ? WorldgenStageStats.beginSchedulingWork(pos, nextStatus) : null;
        if (
            !AdrenalineConfig.parallelWorldgenEnabled()
                || nextStatus == null
                || nextStatus.getIndex() >= ChunkStatus.CARVERS.getIndex()
                || !AdrenalineConfig.parallelChunkStatusEnabled(nextStatus)
        ) {
            if (BuildConfig.DEBUG && nextStatus != null) {
                ChunkStatus scheduledStatus = nextStatus;
                @SuppressWarnings("unchecked")
                Function<ProcessorHandle<Unit>, Runnable> originalTask = (Function<ProcessorHandle<Unit>, Runnable>) accessor.getTask();
                Function<ProcessorHandle<Unit>, Runnable> measuredTask = completionHandle -> () -> {
                    WorldgenStageStats.markDependenciesReady(pos, scheduledStatus);
                    SchedulingWork resumedWork = WorldgenStageStats.beginSchedulingWork(pos, scheduledStatus);
                    Runnable runnable = originalTask.apply(completionHandle);
                    WorldgenStageStats.finishSchedulingWork(resumedWork);
                    runnable.run();
                };
                accessor.setTask(measuredTask);
            }
            if (BuildConfig.DEBUG) {
                WorldgenStageStats.finishSchedulingWork(dispatchWork);
            }
            instance.tell((ChunkTaskPriorityQueueSorter.Message<Runnable>) message);
            return;
        }

        WorldgenWarmup.warmSharedCaches();

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("ChunkMap redirect scheduling pos={},{} inferredStatus={}", pos.x, pos.z, nextStatus);
        }

        ChunkStatus scheduledStatus = nextStatus;
        IntSupplier queueLevel = accessor.getLevel();
        @SuppressWarnings("unchecked")
        Function<ProcessorHandle<Unit>, Runnable> taskFunction = (Function<ProcessorHandle<Unit>, Runnable>) accessor.getTask();
        Function<ProcessorHandle<Unit>, Runnable> wrappedTask = completionHandle -> () -> {
            if (BuildConfig.DEBUG) {
                WorldgenStageStats.markDependenciesReady(pos, scheduledStatus);
            }
            ProcessorHandle<Unit> discardedCompletionHandle = ProcessorHandle.of("adrenaline-wrap", unit -> {
            });
            ChunkJob job;
            if (BuildConfig.DEBUG) {
                SchedulingWork resumedWork = WorldgenStageStats.beginSchedulingWork(pos, scheduledStatus);
                job = new ChunkJob(pos, 0, () -> {
                    SchedulingWork executionWork = WorldgenStageStats.beginSchedulingWork(pos, scheduledStatus);
                    Runnable runnable = taskFunction.apply(discardedCompletionHandle);
                    WorldgenStageStats.finishSchedulingWork(executionWork);
                    runnable.run();
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getFutureIfPresentUnchecked(scheduledStatus);
                    return future == null ? CompletableFuture.completedFuture(null) : future;
                }, () -> {
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getFutureIfPresentUnchecked(scheduledStatus);
                    if (future != null) {
                        future.complete(ChunkHolder.UNLOADED_CHUNK);
                    }
                }, contextClassLoader);
                WorldgenStageStats.finishSchedulingWork(resumedWork);
            } else {
                job = new ChunkJob(pos, 0, () -> {
                    taskFunction.apply(discardedCompletionHandle).run();
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getFutureIfPresentUnchecked(scheduledStatus);
                    return future == null ? CompletableFuture.completedFuture(null) : future;
                }, () -> {
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getFutureIfPresentUnchecked(scheduledStatus);
                    if (future != null) {
                        future.complete(ChunkHolder.UNLOADED_CHUNK);
                    }
                }, contextClassLoader);
            }
            job.trackWaiting(pos, scheduledStatus);
            job.prioritize(queueLevel.getAsInt());
            ChunkJobScheduler.get().submit(job);
            completionHandle.tell(Unit.INSTANCE);
        };
        accessor.setTask(wrappedTask);
        if (BuildConfig.DEBUG) {
            WorldgenStageStats.finishSchedulingWork(dispatchWork);
        }
        instance.tell((ChunkTaskPriorityQueueSorter.Message<Runnable>) message);
    }

    @Inject(
        method = "m_214956_",
        remap = false,
        at = @At("RETURN")
    )
    private void clearHolder(ChunkHolder holder, Runnable task, CallbackInfo ci) {
        this.adrenaline$currentHolder.remove();
    }

}
