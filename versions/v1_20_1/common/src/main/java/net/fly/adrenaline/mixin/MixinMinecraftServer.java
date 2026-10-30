package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.AdrenalinePlatform;
import net.fly.adrenaline.client.InitialWorldCreationAccess;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.scheduler.AdaptiveWorldgenWorkers;
import net.fly.adrenaline.util.DeferredSpawnSearch;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 1100)
public abstract class MixinMinecraftServer implements InitialWorldCreationAccess {

    @Shadow private long nextTickTime;

    @Shadow protected abstract void waitUntilNextTick();

    @Unique
    private int adrenaline$lastSpawnSaveCount;

    @Unique
    private boolean adrenaline$initialWorldCreation;

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$resetDeferredSpawnSearch(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        this.adrenaline$initialWorldCreation = !server.getWorldData().overworldData().isInitialized();
        DeferredSpawnSearch.reset();
        AdaptiveWorldgenWorkers.start(server);
    }

    @Override
    public boolean adrenaline$isInitialWorldCreation() {
        return this.adrenaline$initialWorldCreation;
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$cancelWorldgenJobsOnStop(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        DeferredSpawnSearch.reset();
        ChunkJobScheduler.get().cancel(server);
        AdaptiveWorldgenWorkers.stop(server);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void adrenaline$beginAdaptiveWorkerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        AdaptiveWorldgenWorkers.beginTick((MinecraftServer) (Object) this);
    }

    @WrapMethod(method = "tickServer")
    private void adrenaline$onlyMaintainWarmupChunks(BooleanSupplier shouldKeepTicking, Operation<Void> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!WarmupWorldDiscard.isServer(server)) {
            original.call(shouldKeepTicking);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            level.getChunkSource().tick(shouldKeepTicking, false);
        }
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void adrenaline$finishAdaptiveWorkerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        AdaptiveWorldgenWorkers.endTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void adrenaline$advanceAdaptiveWorkerTick(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.advanceTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"))
    private void adrenaline$advanceAdaptivePreparationTick(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.advancePreparationTick((MinecraftServer) (Object) this);
    }

    @WrapMethod(method = "stopServer")
    private void adrenaline$discardWarmupWorldOnStop(Operation<Void> original) {
        WarmupWorldDiscard.runShutdown((MinecraftServer) (Object) this, () -> original.call());
    }

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipDiscardedWarmupSave(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (WarmupWorldDiscard.isDiscarding()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.INITIAL_SPAWN)
    private static void adrenaline$deferInitialSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci) {
        if (!AdrenalineConfig.initialSpawnOptimizationEnabled() || OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT) || debugWorld || AdrenalineConfig.internalSpawnPreparationRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return;
        }
        if (AdrenalinePlatform.onCreateWorldSpawn(level, levelData)) {
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

    @ModifyConstant(method = "loadLevel", constant = @Constant(intValue = 11), require = 0)
    @ControlsOptimization(Optimization.SPAWN_RADIUS)
    private int adrenaline$useConfiguredSpawnZoneRadiusForProgress(int radius) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS) || OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            return radius;
        }
        if (BackgroundWorldgenWarmupState.isServer((MinecraftServer) (Object) this)) {
            return BackgroundWorldgenWarmupState.spawnZoneRadius();
        }
        if (!this.adrenaline$initialWorldCreation && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return 0;
        }
        int configuredRadius = AdrenalineConfig.resolvedSpawnZoneRadius();
        return configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius;
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "CONSTANT", args = "intValue=441"), require = 0)
    @ControlsOptimization(Optimization.SPAWN_WAIT)
    private int adrenaline$useConfiguredSpawnZoneChunkCount(int chunkCount) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            return chunkCount;
        }
        if (!this.adrenaline$initialWorldCreation && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return 0;
        }
        if (EarlyWorldEntry.canEnter() && !DeferredSpawnSearch.isPending()) {
            return ((MinecraftServer) (Object) this).overworld().getChunkSource().getTickingGenerated();
        }
        int radius;
        if (BackgroundWorldgenWarmupState.isServer((MinecraftServer) (Object) this)) {
            radius = BackgroundWorldgenWarmupState.spawnZoneRadius() + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        } else {
            radius = AdrenalineConfig.internalSpawnPreparationRadius();
        }
        if (radius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return 0;
        }
        int diameter = radius * 2 - 1;
        return diameter * diameter;
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"), require = 0)
    @ControlsOptimization(Optimization.SPAWN_WAIT)
    private int adrenaline$waitForResolvedSpawn(int generated) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            return generated;
        }
        if (DeferredSpawnSearch.isPending()) {
            return -1;
        }
        return Math.min(generated, this.adrenaline$useConfiguredSpawnZoneChunkCount(441));
    }

    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V"), cancellable = true)
    private void adrenaline$stopSpawnPreparationAfterCancellation(ChunkProgressListener progressListener, CallbackInfo ci) {
        if (!((MinecraftServer) (Object) this).isRunning()) {
            DeferredSpawnSearch.reset();
            progressListener.stop();
            ci.cancel();
            return;
        }
        DeferredSpawnSearch.advance(progressListener);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void adrenaline$beginIncrementalSpawnSaving(ChunkProgressListener progressListener, CallbackInfo ci) {
        this.adrenaline$lastSpawnSaveCount = 0;
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void adrenaline$finishWarmupPreparation(ChunkProgressListener progressListener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!BackgroundWorldgenWarmupState.isServer(server) || !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            return;
        }
        ServerLevel level = server.overworld();
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos center = new ChunkPos(level.getSharedSpawnPos());
        int radius = BackgroundWorldgenWarmupState.spawnZoneRadius() + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        int diameter = radius * 2 - 1;
        chunkSource.addRegionTicket(TicketType.START, center, radius, Unit.INSTANCE);
        while (server.isRunning() && BackgroundWorldgenWarmupState.isServer(server)
            && chunkSource.getTickingGenerated() < diameter * diameter) {
            this.nextTickTime = Util.getMillis() + 10L;
            this.waitUntilNextTick();
        }
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
        if (this.adrenaline$initialWorldCreation && AdrenalineConfig.saveChunksAfterWorldCreation() && !BackgroundWorldgenWarmupState.isServer(server)) {
            server.overworld().getChunkSource().save(false);
        }
    }

}
