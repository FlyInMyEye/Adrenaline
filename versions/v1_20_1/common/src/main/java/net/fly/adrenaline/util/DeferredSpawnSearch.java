package net.fly.adrenaline.util;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

public final class DeferredSpawnSearch {

    private static State state;

    private DeferredSpawnSearch() {
    }

    public static synchronized void reset() {
        state = null;
    }

    public static synchronized void begin(ServerLevel level, ServerLevelData levelData, ChunkPos candidate, boolean generateBonusChest) {
        state = new State(level, levelData, candidate, generateBonusChest);
    }

    public static synchronized boolean isPending() {
        return state != null;
    }

    public static synchronized void advance(ChunkProgressListener progressListener) {
        State current = state;
        if (current == null) {
            return;
        }
        ChunkPos searchPos = current.searchPos();
        ServerChunkCache chunkSource = current.level.getChunkSource();
        LevelChunk chunk = chunkSource.getChunkNow(searchPos.x, searchPos.z);
        if (chunk == null) {
            return;
        }
        current.resolveFallback(chunkSource);
        BlockPos spawnPos = PlayerRespawnLogic.getSpawnPosInChunk(current.level, searchPos);
        if (spawnPos != null) {
            current.finish(spawnPos, progressListener);
            state = null;
            return;
        }
        if (!current.moveNext()) {
            current.finish(new BlockPos(current.levelData.getXSpawn(), current.levelData.getYSpawn(), current.levelData.getZSpawn()), progressListener);
            state = null;
        }
    }

    private static final class State {

        private final ServerLevel level;
        private final ServerLevelData levelData;
        private final ChunkPos candidate;
        private final boolean generateBonusChest;
        private int offsetX;
        private int offsetZ;
        private int directionX;
        private int directionZ = -1;
        private int attempts;
        private boolean fallbackResolved;

        private State(ServerLevel level, ServerLevelData levelData, ChunkPos candidate, boolean generateBonusChest) {
            this.level = level;
            this.levelData = levelData;
            this.candidate = candidate;
            this.generateBonusChest = generateBonusChest;
        }

        private ChunkPos searchPos() {
            return new ChunkPos(this.candidate.x + this.offsetX, this.candidate.z + this.offsetZ);
        }

        private boolean moveNext() {
            this.attempts++;
            if (this.attempts >= 121) {
                return false;
            }
            if (this.offsetX == this.offsetZ || this.offsetX < 0 && this.offsetX == -this.offsetZ || this.offsetX > 0 && this.offsetX == 1 - this.offsetZ) {
                int oldDirectionX = this.directionX;
                this.directionX = -this.directionZ;
                this.directionZ = oldDirectionX;
            }
            this.offsetX += this.directionX;
            this.offsetZ += this.directionZ;
            return true;
        }

        private void resolveFallback(ServerChunkCache chunkSource) {
            if (this.fallbackResolved) {
                return;
            }
            int y = chunkSource.getGenerator().getSpawnHeight(this.level);
            if (y < this.level.getMinBuildHeight()) {
                BlockPos origin = this.candidate.getWorldPosition();
                y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX() + 8, origin.getZ() + 8);
            }
            this.levelData.setSpawn(this.candidate.getWorldPosition().offset(8, y, 8), 0.0F);
            this.fallbackResolved = true;
        }

        private void finish(BlockPos spawnPos, ChunkProgressListener progressListener) {
            this.levelData.setSpawn(spawnPos, 0.0F);
            progressListener.updateSpawnPos(new ChunkPos(spawnPos));
            ChunkPos finalChunk = new ChunkPos(spawnPos);
            if (!finalChunk.equals(this.candidate)) {
                int configuredRadius = AdrenalineConfig.internalSpawnPreparationRadius();
                int ticketRadius = configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius;
                ServerChunkCache chunkSource = this.level.getChunkSource();
                chunkSource.removeRegionTicket(TicketType.START, this.candidate, ticketRadius, Unit.INSTANCE);
                chunkSource.addRegionTicket(TicketType.START, finalChunk, ticketRadius, Unit.INSTANCE);
            }
            if (this.generateBonusChest) {
                this.level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(registry -> registry.getHolder(MiscOverworldFeatures.BONUS_CHEST)).ifPresent(feature -> feature.value().place(this.level, this.level.getChunkSource().getGenerator(), this.level.random, spawnPos));
            }
        }
    }
}
