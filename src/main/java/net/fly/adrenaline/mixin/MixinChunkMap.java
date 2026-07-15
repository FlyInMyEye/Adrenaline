package net.fly.adrenaline.mixin;

import java.util.function.Function;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldgenWarmup;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.level.chunk.ChunkAccess;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    private static final ThreadLocal<ChunkHolder> CURRENT_HOLDER = new ThreadLocal<>();
    private static final Map<ChunkHolder, Deque<ChunkStatus>> PENDING_STATUS = new ConcurrentHashMap<>();
    @Inject(
        method = "scheduleChunkGeneration",
        at = @At("HEAD")
    )
    private void captureStatus(ChunkHolder holder, ChunkStatus status, CallbackInfoReturnable<CompletableFuture<?>> cir) {
        PENDING_STATUS.computeIfAbsent(holder, ignored -> new ConcurrentLinkedDeque<>()).addLast(status);
    }

    @Inject(
        method = "m_214956_",
        remap = false,
        at = @At("HEAD")
    )
    private void captureHolder(ChunkHolder holder, Runnable task, CallbackInfo ci) {
        CURRENT_HOLDER.set(holder);
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
        ChunkHolder holder = CURRENT_HOLDER.get();
        ChunkStatus nextStatus = null;
        if (holder != null) {
            Deque<ChunkStatus> pending = PENDING_STATUS.get(holder);
            if (pending != null) {
                nextStatus = pending.pollFirst();
                if (pending.isEmpty()) {
                    PENDING_STATUS.remove(holder, pending);
                }
            }
        }
        if (nextStatus == null) {
            nextStatus = nextStatus(holder);
        }
        if (
            !AdrenalineConfig.parallelWorldgenEnabled()
                || nextStatus == null
                || nextStatus.getIndex() >= ChunkStatus.FEATURES.getIndex()
                || !AdrenalineConfig.parallelChunkStatusEnabled(nextStatus)
        ) {
            instance.tell((ChunkTaskPriorityQueueSorter.Message<Runnable>) message);
            return;
        }

        WorldgenWarmup.warmSharedCaches();

        MixinMessageAccessor accessor = (MixinMessageAccessor) (Object) message;
        ChunkPos pos = new ChunkPos(accessor.getPos());
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        if (AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("ChunkMap redirect scheduling pos={},{} inferredStatus={}", pos.x, pos.z, nextStatus);
        }

        int writeRadius = nextStatus == ChunkStatus.CARVERS ? AdrenalineConfig.resolvedFeatureSafetyRadius() : 0;
        ChunkStatus scheduledStatus = nextStatus;

        ChunkJobScheduler.get().submit(new ChunkJob(pos, writeRadius, () -> {
            @SuppressWarnings("unchecked")
            Function<ProcessorHandle<Unit>, Runnable> taskFunction = (Function<ProcessorHandle<Unit>, Runnable>) accessor.getTask();
            ProcessorHandle<Unit> dummy = ProcessorHandle.of("adrenaline-wrap", unit -> {
            });
            taskFunction.apply(dummy).run();
        }, () -> {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getFutureIfPresentUnchecked(scheduledStatus);
            if (future != null) {
                future.complete(ChunkHolder.UNLOADED_CHUNK);
            }
        }, contextClassLoader));
    }

    @Inject(
        method = "m_214956_",
        remap = false,
        at = @At("RETURN")
    )
    private void clearHolder(ChunkHolder holder, Runnable task, CallbackInfo ci) {
        CURRENT_HOLDER.remove();
    }

    private static ChunkStatus nextStatus(ChunkHolder holder) {
        if (holder == null) {
            return null;
        }

        ChunkStatus last = holder.getLastAvailableStatus();
        if (last == null) {
            return ChunkStatus.STRUCTURE_STARTS;
        }

        int nextIndex = last.getIndex() + 1;
        return nextIndex < ChunkStatus.getStatusList().size() ? ChunkStatus.getStatusList().get(nextIndex) : null;
    }

}
