package net.fly.adrenaline.util;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.mixin.MixinChunkLevelAccessor;
import net.fly.adrenaline.mixin.MixinChunkTaskPriorityQueueAccessor;
import net.fly.adrenaline.mixin.MixinChunkPyramidAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public final class FeatureSafety {

    private static ChunkPyramid original;
    private static ChunkPyramid installed;
    private static MinecraftServer server;
    private static volatile int activeRadius;

    private FeatureSafety() {
    }

    public static int activeRadius() {
        return activeRadius;
    }

    public static void start(MinecraftServer owner) {
        boolean interrupted = false;
        try {
            while (true) {
                MinecraftServer previous;
                synchronized (FeatureSafety.class) {
                    if (server == owner) {
                        return;
                    }
                    if (server == null) {
                        install(owner);
                        return;
                    }
                    previous = server;
                }
                if (previous.isRunning()) {
                    throw new IllegalStateException("Cannot change feature dependencies while another server is running");
                }
                try {
                    previous.getRunningThread().join();
                    stop(previous);
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void install(MinecraftServer owner) {
        if (original == null || ChunkPyramid.GENERATION_PYRAMID != installed) {
            original = ChunkPyramid.GENERATION_PYRAMID;
        }
        int radius = AdrenalineConfig.configuredFeatureSafetyRadius();
        ChunkPyramid pyramid = original;
        if (radius > AdrenalineConfig.DEFAULT_FEATURE_SAFETY_RADIUS) {
            ChunkPyramid.Builder builder = new ChunkPyramid.Builder();
            for (ChunkStep step : original.steps()) {
                builder.step(step.targetStatus(), next -> {
                    for (int distance = 0; distance < step.directDependencies().size(); distance++) {
                        next.addRequirement(step.directDependencies().get(distance), distance);
                    }
                    next.blockStateWriteRadius(step.blockStateWriteRadius()).setTask(step.task());
                    if (step.targetStatus() == ChunkStatus.FEATURES) {
                        next.addRequirement(ChunkStatus.CARVERS, radius);
                        next.blockStateWriteRadius(Math.max(radius, step.blockStateWriteRadius()));
                    }
                    return next;
                });
            }
            pyramid = builder.build();
        }
        ChunkStep full = pyramid.getStepTo(ChunkStatus.FULL);
        int dependencyRadius = full.accumulatedDependencies().getRadius();
        MixinChunkPyramidAccessor.adrenaline$setGenerationPyramid(pyramid);
        MixinChunkLevelAccessor.adrenaline$setFullChunkStep(full);
        MixinChunkLevelAccessor.adrenaline$setRadiusAroundFullChunk(dependencyRadius);
        MixinChunkLevelAccessor.adrenaline$setMaxLevel(33 + dependencyRadius);
        MixinChunkTaskPriorityQueueAccessor.adrenaline$setPriorityLevelCount(35 + dependencyRadius);
        installed = pyramid;
        activeRadius = radius;
        server = owner;
    }

    public static synchronized void stop(MinecraftServer owner) {
        if (server == owner) {
            server = null;
            activeRadius = 0;
        }
    }
}
