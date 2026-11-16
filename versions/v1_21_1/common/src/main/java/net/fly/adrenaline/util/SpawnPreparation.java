package net.fly.adrenaline.util;

import java.util.Comparator;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

public final class SpawnPreparation implements AutoCloseable {

    private static final TicketType<ChunkPos> PREPARATION_TICKET = TicketType.create("adrenaline_spawn_preparation", Comparator.comparingLong(ChunkPos::toLong));

    private final ServerLevel level;
    private final ChunkPos center;
    private final int radius;

    public SpawnPreparation(ServerLevel level, int radius) {
        this.level = level;
        this.center = new ChunkPos(level.getSharedSpawnPos());
        this.radius = radius;
        if (radius > AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            level.getChunkSource().addRegionTicket(PREPARATION_TICKET, this.center, radius, this.center);
        }
    }

    public int progressRadius() {
        return this.radius - 1;
    }

    @Override
    public void close() {
        if (this.radius > AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            this.level.getChunkSource().removeRegionTicket(PREPARATION_TICKET, this.center, this.radius, this.center);
        }
    }
}
