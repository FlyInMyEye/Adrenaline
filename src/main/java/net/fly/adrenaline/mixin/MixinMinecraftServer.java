package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.DeferredSpawnSearch;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1100)
public class MixinMinecraftServer {

    @Unique
    private int adrenaline$lastSpawnSaveCount;

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$resetDeferredSpawnSearch(CallbackInfo ci) {
        DeferredSpawnSearch.reset();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$cancelWorldgenJobsOnStop(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        ChunkJobScheduler.get().cancel(server);
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
        if (BackgroundWorldgenWarmupState.isServer((MinecraftServer) (Object) this)) {
            return BackgroundWorldgenWarmupState.SPAWN_ZONE_RADIUS;
        }
        int configuredRadius = AdrenalineConfig.resolvedSpawnZoneRadius();
        return configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius;
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "CONSTANT", args = "intValue=441"))
    private int adrenaline$useConfiguredSpawnZoneChunkCount(int chunkCount) {
        if (EarlyWorldEntry.canEnter() && !DeferredSpawnSearch.isPending()) {
            return ((MinecraftServer) (Object) this).overworld().getChunkSource().getTickingGenerated();
        }
        int radius;
        if (BackgroundWorldgenWarmupState.isServer((MinecraftServer) (Object) this)) {
            radius = BackgroundWorldgenWarmupState.SPAWN_ZONE_RADIUS + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        } else {
            radius = AdrenalineConfig.internalSpawnPreparationRadius();
        }
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

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void adrenaline$beginIncrementalSpawnSaving(ChunkProgressListener progressListener, CallbackInfo ci) {
        this.adrenaline$lastSpawnSaveCount = 0;
    }

    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V", ordinal = 0))
    private void adrenaline$saveGeneratedSpawnChunks(ChunkProgressListener progressListener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (BackgroundWorldgenWarmupState.isServer(server)) {
            return;
        }
        ServerChunkCache chunkSource = server.overworld().getChunkSource();
        int generated = chunkSource.getTickingGenerated();
        int interval = AdrenalineConfig.incrementalSaveInterval();
        if (interval > 0 && generated - this.adrenaline$lastSpawnSaveCount >= interval) {
            chunkSource.save(false);
            this.adrenaline$lastSpawnSaveCount = generated;
        }
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void adrenaline$finishIncrementalSpawnSaving(ChunkProgressListener progressListener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (AdrenalineConfig.saveChunksAfterWorldCreation() && !BackgroundWorldgenWarmupState.isServer(server)) {
            server.overworld().getChunkSource().save(false);
        }
    }

}
