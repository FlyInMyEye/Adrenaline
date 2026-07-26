package net.fly.adrenaline.mixin;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.SchedulingWork;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
public class MixinChunkStatus {

    @Shadow
    @Final
    private static List<ChunkStatus> STATUS_BY_RANGE;

    @Shadow
    @Final
    private static IntList RANGE_BY_STATUS;

    @Inject(method = "getStatusAroundFullChunk", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$useConfiguredStatusByRange(int distance, CallbackInfoReturnable<ChunkStatus> cir) {
        int extraRadius = AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        if (extraRadius <= 0 || distance < 3) {
            return;
        }
        if (distance <= 2 + extraRadius) {
            cir.setReturnValue(ChunkStatus.CARVERS);
            return;
        }

        int vanillaDistance = distance - extraRadius;
        cir.setReturnValue(vanillaDistance >= STATUS_BY_RANGE.size() ? ChunkStatus.EMPTY : STATUS_BY_RANGE.get(vanillaDistance));
    }

    @Inject(method = "maxDistance", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$useConfiguredMaxDistance(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(STATUS_BY_RANGE.size() + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1);
    }

    @Inject(method = "getDistance", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$useConfiguredStatusDistance(ChunkStatus status, CallbackInfoReturnable<Integer> cir) {
        int distance = RANGE_BY_STATUS.getInt(status.getIndex());
        if (status.getIndex() <= ChunkStatus.CARVERS.getIndex()) {
            distance += AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        }
        cir.setReturnValue(distance);
    }

    @ModifyArg(
        method = "m_279978_",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/WorldGenRegion;<init>(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkStatus;I)V"
        ),
        index = 3
    )
    private static int adrenaline$useConfiguredFeatureWriteRadius(int radius) {
        return AdrenalineConfig.resolvedFeatureSafetyRadius();
    }

    @Redirect(
        method = "m_280308_",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkStatus$GenerationTask;m_214024_(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
            remap = false
        )
    )
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> adrenaline$scheduleFeaturesAtExecutionPoint(
        @Coerce Object generationTask,
        ChunkStatus status,
        Executor executor,
        ServerLevel level,
        ChunkGenerator generator,
        StructureTemplateManager structureTemplateManager,
        ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
        List<ChunkAccess> chunks,
        ChunkAccess centerChunk
    ) {
        if (BuildConfig.DEBUG) {
            WorldgenStageStats.markDependenciesReady(centerChunk.getPos(), status);
        }
        if ((Object) this == ChunkStatus.FEATURES && AdrenalineConfig.parallelWorldgenEnabled() && AdrenalineConfig.parallelChunkStatusEnabled(status)) {
            return scheduleFeature(generationTask, status, executor, level, generator, structureTemplateManager, lightEngine, fullChunkConverter, chunks, centerChunk);
        }
        long startedNanos = BuildConfig.DEBUG ? WorldgenStageStats.beginStage(centerChunk.getPos(), status) : 0L;
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = invokeGenerationTask(generationTask, status, executor, level, generator, structureTemplateManager, lightEngine, fullChunkConverter, chunks, centerChunk);
        if (BuildConfig.DEBUG) {
            return WorldgenStageStats.track(status, centerChunk.getPos(), startedNanos, future);
        }
        return future;
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleFeature(
        Object generationTask,
        ChunkStatus status,
        Executor executor,
        ServerLevel level,
        ChunkGenerator generator,
        StructureTemplateManager structureTemplateManager,
        ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
        List<ChunkAccess> chunks,
        ChunkAccess centerChunk
    ) {
        int writeRadius = AdrenalineConfig.resolvedFeatureSafetyRadius();
        ChunkPos centerPos = centerChunk.getPos();
        SchedulingWork schedulingWork = BuildConfig.DEBUG ? WorldgenStageStats.beginSchedulingWork(centerPos, status) : null;

        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info(
                "Scheduling feature generation center={},{} featureSafetyRadius={} chunkCount={}\n{}",
                centerPos.x,
                centerPos.z,
                writeRadius,
                chunks.size(),
                chunkStageGrid(chunks)
            );
        }

        Set<Long> footprint = buildCenterFootprint(centerPos, writeRadius);
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> result = new CompletableFuture<>();
        String debugLabel = centerPos.x + "," + centerPos.z + " status=" + status + " footprint=" + summarizeFootprint(footprint);

        ChunkJob job = new ChunkJob(footprint, () -> {
            long startedNanos = BuildConfig.DEBUG ? WorldgenStageStats.beginStage(centerPos, status) : 0L;
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = invokeGenerationTask(
                generationTask,
                status,
                executor,
                level,
                generator,
                structureTemplateManager,
                lightEngine,
                fullChunkConverter,
                chunks,
                centerChunk
            );
            if (BuildConfig.DEBUG) {
                WorldgenStageStats.track(status, centerPos, startedNanos, future);
            }
            future.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(value);
                }
            });
            return future;
        }, () -> result.complete(ChunkHolder.UNLOADED_CHUNK), contextClassLoader, debugLabel);
        job.trackWaiting(centerPos, status);
        if (BuildConfig.DEBUG) {
            WorldgenStageStats.finishSchedulingWork(schedulingWork);
        }
        ChunkJobScheduler.get().submit(job);

        return result;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> invokeGenerationTask(
        Object generationTask,
        ChunkStatus status,
        Executor executor,
        ServerLevel level,
        ChunkGenerator generator,
        StructureTemplateManager structureTemplateManager,
        ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
        List<ChunkAccess> chunks,
        ChunkAccess centerChunk
    ) {
        try {
            Method method = generationTask.getClass().getMethod(
                "m_214024_",
                ChunkStatus.class,
                Executor.class,
                ServerLevel.class,
                ChunkGenerator.class,
                StructureTemplateManager.class,
                ThreadedLevelLightEngine.class,
                Function.class,
                List.class,
                ChunkAccess.class
            );
            method.setAccessible(true);
            return (CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>) method.invoke(
                generationTask,
                status,
                executor,
                level,
                generator,
                structureTemplateManager,
                lightEngine,
                fullChunkConverter,
                chunks,
                centerChunk
            );
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<Long> buildCenterFootprint(ChunkPos center, int writeRadius) {
        Set<Long> footprint = new HashSet<>();
        for (int dx = -writeRadius; dx <= writeRadius; dx++) {
            for (int dz = -writeRadius; dz <= writeRadius; dz++) {
                footprint.add(new ChunkPos(center.x + dx, center.z + dz).toLong());
            }
        }
        return footprint;
    }

    private static String chunkStageGrid(List<ChunkAccess> chunks) {
        Map<Long, Integer> stages = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ChunkAccess chunk : chunks) {
            ChunkPos pos = chunk.getPos();
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
            ChunkStatus chunkStatus = chunk.getStatus();
            stages.put(pos.toLong(), chunkStatus == null ? 0 : chunkStatus.getIndex() + 1);
        }

        StringBuilder builder = new StringBuilder();
        for (int z = minZ; z <= maxZ; z++) {
            if (z != minZ) {
                builder.append('\n');
            }
            for (int x = minX; x <= maxX; x++) {
                int stage = stages.getOrDefault(new ChunkPos(x, z).toLong(), 0);
                builder.append(stage <= 9 ? (char) ('0' + stage) : '+');
            }
        }
        return builder.toString();
    }

    private static String summarizeFootprint(Set<Long> footprint) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : footprint) {
            ChunkPos pos = new ChunkPos(key);
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return "size=" + footprint.size() + " bounds=[" + minX + "," + minZ + " -> " + maxX + "," + maxZ + "]";
    }
}
