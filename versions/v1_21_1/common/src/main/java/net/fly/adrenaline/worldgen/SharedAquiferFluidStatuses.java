package net.fly.adrenaline.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.levelgen.Aquifer;

public final class SharedAquiferFluidStatuses {

    private static final int SHARD_COUNT = 32;
    private static final int ENTRIES_PER_SHARD = 4096;
    private final Shard[] shards = new Shard[SHARD_COUNT];

    public SharedAquiferFluidStatuses() {
        for (int index = 0; index < SHARD_COUNT; index++) {
            this.shards[index] = new Shard();
        }
    }

    public Aquifer.FluidStatus get(long location) {
        return this.shard(location).get(location);
    }

    public Aquifer.FluidStatus put(long location, Aquifer.FluidStatus status) {
        return this.shard(location).put(location, status);
    }

    private Shard shard(long location) {
        int hash = (int) (location ^ location >>> 32);
        hash ^= hash >>> 16;
        return this.shards[hash & (SHARD_COUNT - 1)];
    }

    private static final class Shard {

        private final Long2ObjectLinkedOpenHashMap<Aquifer.FluidStatus> statuses = new Long2ObjectLinkedOpenHashMap<>();

        private synchronized Aquifer.FluidStatus get(long location) {
            return this.statuses.getAndMoveToLast(location);
        }

        private synchronized Aquifer.FluidStatus put(long location, Aquifer.FluidStatus status) {
            Aquifer.FluidStatus existing = this.statuses.getAndMoveToLast(location);
            if (existing != null) {
                return existing;
            }
            this.statuses.putAndMoveToLast(location, status);
            if (this.statuses.size() > ENTRIES_PER_SHARD) {
                this.statuses.removeFirst();
            }
            return status;
        }
    }
}
