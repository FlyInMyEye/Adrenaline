package net.fly.adrenaline.scheduler;

import java.util.Set;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.GenerationJob.WaitReason;
import net.minecraft.world.level.ChunkPos;

public final class ChunkJobScheduler extends GenerationScheduler {

    private static final ChunkJobScheduler INSTANCE = new ChunkJobScheduler();

    private ChunkJobScheduler() {
        super(AdrenalineConfig.resolvedGenerationWorkerThreads(), BuildConfig.DEBUG);
    }

    public static ChunkJobScheduler get() {
        return INSTANCE;
    }

    @Override
    protected int configuredWorkerThreads() {
        return AdrenalineConfig.resolvedGenerationWorkerThreads();
    }

    @Override
    protected GenerationTaskQueue.Priority stagePriority() {
        return switch (AdrenalineConfig.stagePriority()) {
            case FIFO -> GenerationTaskQueue.Priority.FIFO;
            case HIGHEST -> GenerationTaskQueue.Priority.HIGHEST;
            case NEAREST -> GenerationTaskQueue.Priority.NEAREST;
        };
    }

    @Override
    protected boolean debugLoggingEnabled() {
        return AdrenalineConfig.debugLoggingEnabled();
    }

    @Override
    protected void onQueued(GenerationJob job, WaitReason reason) {
        GlobalCommon.LOGGER.info(reason == WaitReason.FOOTPRINT_CONFLICT
            ? "Queued conflicting job {} with footprint {}" : "Queued capacity job {} with footprint {}",
            job.debugLabel(), summarizeFootprint(job.footprint()));
    }

    @Override
    protected void onDispatched(GenerationJob job) {
        GlobalCommon.LOGGER.info("Dispatching job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
    }

    @Override
    protected void onCancelled(int pendingCount, int activeJobCount) {
        GlobalCommon.LOGGER.info("Cancelled {} pending worldgen jobs; {} active jobs will drain", pendingCount, activeJobCount);
    }

    public synchronized void submit(ChunkJob job) {
        super.submit(job);
    }

    public synchronized void awaitNotActive(ChunkPos pos) throws InterruptedException {
        super.awaitNotActive(pos.toLong());
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
