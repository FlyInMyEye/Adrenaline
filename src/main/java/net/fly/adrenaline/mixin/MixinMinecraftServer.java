package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.DeferredSpawnSearch;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$resetDeferredSpawnSearch(CallbackInfo ci) {
        DeferredSpawnSearch.reset();
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$deferInitialSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci) {
        if (!AdrenalineConfig.initialSpawnOptimizationEnabled() || debugWorld || AdrenalineConfig.internalSpawnPreparationRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return;
        }
        if (ForgeEventFactory.onCreateWorldSpawn(level, levelData)) {
            ci.cancel();
            return;
        }
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos candidate = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
        int y = Math.max(level.getMinBuildHeight(), chunkSource.getGenerator().getSpawnHeight(level));
        BlockPos provisionalSpawn = candidate.getWorldPosition().offset(8, y, 8);
        levelData.setSpawn(provisionalSpawn, 0.0F);
        DeferredSpawnSearch.begin(level, levelData, candidate, generateBonusChest);
        ci.cancel();
    }

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
        if (EarlyWorldEntry.canEnter() && !DeferredSpawnSearch.isPending()) {
            return ((MinecraftServer) (Object) this).overworld().getChunkSource().getTickingGenerated();
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
        DeferredSpawnSearch.advance(progressListener);
        if (!((MinecraftServer) (Object) this).isRunning()) {
            progressListener.stop();
            ci.cancel();
        }
    }

}
