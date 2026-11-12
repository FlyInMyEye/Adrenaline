package net.fly.adrenaline.scheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.WaitingReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class ChunkJob extends GenerationJob {

    private static final ConcurrentHashMap<ServerLevel, long[]> PRIORITY_FOCI = new ConcurrentHashMap<>();

    private ChunkPos waitingPos;
    private ChunkStatus waitingStatus;
    private WaitingReason waitingReason;
    private long waitingStartedNanos;
    private ServerLevel priorityLevel;
    private ChunkPos priorityPos;
    private long[] cachedPriorityFoci;
    private long cachedProximity = Long.MAX_VALUE;

    public ChunkJob(ChunkPos center, int writeRadius, Runnable work, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), asAsync(work), () -> {
        }, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(ChunkPos center, int writeRadius, Runnable work, Runnable cancelWork, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), asAsync(work), cancelWork, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(ChunkPos center, int writeRadius, Supplier<CompletableFuture<?>> work, Runnable cancelWork, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), work, cancelWork, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(Set<Long> footprint, Runnable work, ClassLoader contextClassLoader) {
        this(footprint, asAsync(work), () -> {
        }, contextClassLoader, "custom");
    }

    public ChunkJob(Set<Long> footprint, Runnable work, ClassLoader contextClassLoader, String debugLabel) {
        this(footprint, asAsync(work), () -> {
        }, contextClassLoader, debugLabel);
    }

    public ChunkJob(Set<Long> footprint, Runnable work, Runnable cancelWork, ClassLoader contextClassLoader, String debugLabel) {
        this(footprint, asAsync(work), cancelWork, contextClassLoader, debugLabel);
    }

    public ChunkJob(Set<Long> footprint, Supplier<CompletableFuture<?>> work, Runnable cancelWork, ClassLoader contextClassLoader, String debugLabel) {
        super(footprint, work, cancelWork, contextClassLoader, debugLabel);
    }

    @Override
    public long trackedPosition() {
        ChunkPos pos = this.waitingPos;
        return pos == null ? Long.MIN_VALUE : pos.toLong();
    }

    @Override
    protected Execution execution() {
        return ChunkJobScheduler.get();
    }

    public ChunkJob trackWaiting(ChunkPos pos, ChunkStatus status) {
        this.waitingPos = pos;
        this.waitingStatus = status;
        return this;
    }

    @Override
    public int stageIndex() {
        return this.waitingStatus == null ? Integer.MIN_VALUE : this.waitingStatus.getIndex();
    }

    @Override
    public long proximity() {
        if (this.priorityLevel == null || this.priorityPos == null) {
            return Long.MAX_VALUE;
        }

        long[] foci = PRIORITY_FOCI.get(this.priorityLevel);
        if (foci == null) {
            long[] spawnFocus = new long[]{new ChunkPos(this.priorityLevel.getSharedSpawnPos()).toLong()};
            long[] existing = PRIORITY_FOCI.putIfAbsent(this.priorityLevel, spawnFocus);
            foci = existing == null ? spawnFocus : existing;
        }
        if (foci == this.cachedPriorityFoci) {
            return this.cachedProximity;
        }

        long nearest = Long.MAX_VALUE;
        for (long focusKey : foci) {
            long dx = this.priorityPos.x - ChunkPos.getX(focusKey);
            long dz = this.priorityPos.z - ChunkPos.getZ(focusKey);
            nearest = Math.min(nearest, Math.max(Math.abs(dx), Math.abs(dz)));
        }
        this.cachedPriorityFoci = foci;
        this.cachedProximity = nearest;
        return nearest;
    }

    public void prioritizeNearest(ServerLevel level, ChunkPos pos) {
        this.priorityLevel = level;
        this.priorityPos = pos;
    }

    public static void updatePriorityFoci(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long[] foci;
            if (level.players().isEmpty()) {
                foci = new long[]{new ChunkPos(level.getSharedSpawnPos()).toLong()};
            } else {
                foci = new long[level.players().size()];
                for (int i = 0; i < foci.length; i++) {
                    ServerPlayer player = level.players().get(i);
                    foci[i] = player.chunkPosition().toLong();
                }
            }
            long[] previous = PRIORITY_FOCI.get(level);
            if (!Arrays.equals(previous, foci)) {
                PRIORITY_FOCI.put(level, foci);
            }
        }
    }

    public static void clearPriorityFoci() {
        PRIORITY_FOCI.clear();
    }

    @Override
    protected synchronized void transitionWaiting(WaitReason reason) {
        if (this.waitingPos == null || this.waitingStatus == null || !WorldgenStageStats.isEnabled()) {
            return;
        }
        long now = System.nanoTime();
        if (this.waitingReason != null) {
            WorldgenStageStats.addWaitingInterval(this.waitingPos, this.waitingStatus, this.waitingReason, this.waitingStartedNanos, now);
        }
        this.waitingReason = switch (reason) {
            case FOOTPRINT_CONFLICT -> WaitingReason.FOOTPRINT_CONFLICT;
            case CAPACITY -> WaitingReason.CAPACITY;
            case EXECUTOR_QUEUE -> WaitingReason.EXECUTOR_QUEUE;
        };
        this.waitingStartedNanos = now;
    }

    @Override
    protected synchronized void finishWaiting() {
        if (this.waitingReason == null) {
            return;
        }
        long now = System.nanoTime();
        WorldgenStageStats.addWaitingInterval(this.waitingPos, this.waitingStatus, this.waitingReason, this.waitingStartedNanos, now);
        this.waitingReason = null;
        this.waitingStartedNanos = 0L;
    }

    private static Set<Long> buildFootprint(ChunkPos center, int radius) {
        if (radius <= 0) {
            Set<Long> s = new HashSet<>(2);
            s.add(center.toLong());
            return s;
        }
        int side = radius * 2 + 1;
        Set<Long> s = new HashSet<>(side * side * 2);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                s.add(new ChunkPos(center.x + dx, center.z + dz).toLong());
            }
        }
        return s;
    }
}
