package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    private static final int[] SPAWN_CHUNK_OFFSETS = {0, 0, -5, 0, 5, 0, 0, -15, 0, 15, -30, 0, 30, 0, 0, -50, 0, 50, -75, 0, 75, 0, 0, -105, 0, 105};

    @ModifyConstant(method = "loadLevel", constant = @Constant(intValue = 11))
    private int adrenaline$useConfiguredSpawnZoneRadiusForProgress(int radius) {
        int configuredRadius = AdrenalineConfig.resolvedSpawnZoneRadius();
        return configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius;
    }

    @ModifyConstant(method = "prepareLevels", constant = @Constant(intValue = 11))
    private int adrenaline$useConfiguredSpawnZoneRadiusForTickets(int radius) {
        int configuredRadius = AdrenalineConfig.internalSpawnPreparationRadius();
        return configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius;
    }

    @ModifyConstant(method = "prepareLevels", constant = @Constant(intValue = 441))
    private int adrenaline$useConfiguredSpawnZoneChunkCount(int chunkCount) {
        int radius = AdrenalineConfig.internalSpawnPreparationRadius();
        if (radius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return 0;
        }
        int diameter = radius * 2 - 1;
        return diameter * diameter;
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"))
    private int adrenaline$finishSpawnPreparationForEarlyEntry(ServerChunkCache chunkSource) {
        if (!EarlyWorldEntry.canEnter()) {
            return chunkSource.getTickingGenerated();
        }
        int radius = AdrenalineConfig.internalSpawnPreparationRadius();
        if (radius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return 0;
        }
        int diameter = radius * 2 - 1;
        return diameter * diameter;
    }

    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V"), cancellable = true)
    private void adrenaline$stopSpawnPreparationAfterCancellation(ChunkProgressListener progressListener, CallbackInfo ci) {
        if (!((MinecraftServer) (Object) this).isRunning()) {
            progressListener.stop();
            ci.cancel();
        }
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$fastInitialSpawnSetup(ServerLevel level, net.minecraft.world.level.storage.ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci) {
        if (!AdrenalineConfig.initialSpawnOptimizationEnabled()) {
            return;
        }

        if (debugWorld) {
            levelData.setSpawn(BlockPos.ZERO.above(80), 0.0F);
            ci.cancel();
            return;
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos spawnChunk = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
        ChunkGenerator generator = chunkSource.getGenerator();

        BlockPos spawnPos = null;
        for (int i = 0; i < SPAWN_CHUNK_OFFSETS.length; i += 2) {
            ChunkPos candidateChunk = new ChunkPos(spawnChunk.x + SPAWN_CHUNK_OFFSETS[i], spawnChunk.z + SPAWN_CHUNK_OFFSETS[i + 1]);
            BlockPos candidatePos = adrenaline$findChunkCenterSpawn(level, generator, candidateChunk);
            if (candidatePos != null) {
                spawnPos = candidatePos;
                break;
            }
        }

        if (spawnPos == null) {
            spawnPos = adrenaline$findFallbackSpawn(level, generator, spawnChunk);
        }

        levelData.setSpawn(spawnPos, 0.0F);
        ci.cancel();
    }

    private static BlockPos adrenaline$findChunkCenterSpawn(ServerLevel level, ChunkGenerator generator, ChunkPos chunkPos) {
        int x = chunkPos.getMinBlockX() + 8;
        int z = chunkPos.getMinBlockZ() + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y < level.getMinBuildHeight()) {
            y = generator.getSpawnHeight(level);
        }
        if (y < level.getMinBuildHeight()) {
            return null;
        }

        BlockPos spawnPos = new BlockPos(x, y, z);
        BlockPos floorPos = spawnPos.below();
        BlockState floorState = level.getBlockState(floorPos);
        FluidState floorFluid = level.getFluidState(floorPos);
        if (!floorFluid.isEmpty()) {
            return null;
        }
        if (floorState.is(BlockTags.INVALID_SPAWN_INSIDE) || !floorState.isFaceSturdy(level, floorPos, Direction.UP)) {
            return null;
        }
        if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()) {
            return null;
        }
        if (!level.getBlockState(spawnPos.above()).getCollisionShape(level, spawnPos.above()).isEmpty()) {
            return null;
        }
        return spawnPos;
    }

    private static BlockPos adrenaline$findFallbackSpawn(ServerLevel level, ChunkGenerator generator, ChunkPos chunkPos) {
        int x = chunkPos.getMinBlockX() + 8;
        int z = chunkPos.getMinBlockZ() + 8;
        int y = generator.getSpawnHeight(level);
        if (y < level.getMinBuildHeight()) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        return new BlockPos(x, Math.max(y, level.getMinBuildHeight()), z);
    }
}
