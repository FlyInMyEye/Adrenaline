package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BooleanSupplier;
import net.fly.adrenaline.client.InitialWorldCreationAccess;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.AdaptiveWorldgenWorkers;
import net.fly.adrenaline.util.DeferredSpawnSearch;
import net.fly.adrenaline.util.SpawnPreparation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1100)
public abstract class MixinMinecraftServer implements DeferredSpawnSearch.Access, InitialWorldCreationAccess {

    @Unique
    private final DeferredSpawnSearch adrenaline$spawnSearch = new DeferredSpawnSearch();

    @Unique
    private SpawnPreparation adrenaline$spawnPreparation;

    @Unique
    private int adrenaline$lastSpawnSaveCount;

    @Unique
    private boolean adrenaline$initialWorldCreation;

    @Override
    public boolean adrenaline$isInitialWorldCreation() {
        return this.adrenaline$initialWorldCreation;
    }

    @Override
    public DeferredSpawnSearch adrenaline$getSpawnSearch() {
        return this.adrenaline$spawnSearch;
    }

    @Inject(method = "setInitialSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"), cancellable = true)
    @ControlsOptimization(Optimization.INITIAL_SPAWN)
    private static void adrenaline$deferInitialSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci) {
        if (!AdrenalineConfig.initialSpawnOptimizationEnabled() || OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS)
            || debugWorld || AdrenalineConfig.internalSpawnPreparationRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return;
        }
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos candidate = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
        int y = Math.max(level.getMinBuildHeight(), chunkSource.getGenerator().getSpawnHeight(level));
        levelData.setSpawn(candidate.getWorldPosition().offset(8, y, 8), 0.0F);
        ((DeferredSpawnSearch.Access) level.getServer()).adrenaline$getSpawnSearch().begin(level, levelData, candidate, generateBonusChest);
        ci.cancel();
    }

    @WrapOperation(method = "loadLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/progress/ChunkProgressListenerFactory;create(I)Lnet/minecraft/server/level/progress/ChunkProgressListener;"))
    @ControlsOptimization(Optimization.SPAWN_RADIUS)
    private ChunkProgressListener adrenaline$useConfiguredSpawnProgress(ChunkProgressListenerFactory factory, int radius, Operation<ChunkProgressListener> original) {
        if (!OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS) && !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            radius = this.adrenaline$skipSpawnPreparation() ? 0 : AdrenalineConfig.resolvedSpawnZoneRadius() - 1;
        }
        return original.call(factory, radius);
    }

    @WrapMethod(method = "prepareLevels")
    @ControlsOptimization({Optimization.SPAWN_RADIUS, Optimization.SPAWN_WAIT})
    private void adrenaline$prepareConfiguredSpawn(ChunkProgressListener listener, Operation<Void> original) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS) || OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            original.call(listener);
            return;
        }
        int radius = this.adrenaline$skipSpawnPreparation() ? AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS : AdrenalineConfig.internalSpawnPreparationRadius();
        try (SpawnPreparation preparation = new SpawnPreparation(((MinecraftServer) (Object) this).overworld(), radius)) {
            this.adrenaline$spawnPreparation = preparation;
            original.call(listener);
        } finally {
            this.adrenaline$spawnSearch.reset();
            this.adrenaline$spawnPreparation = null;
        }
    }

    @Unique
    private boolean adrenaline$skipSpawnPreparation() {
        return !this.adrenaline$initialWorldCreation && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF;
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"))
    @ControlsOptimization(Optimization.SPAWN_WAIT)
    private int adrenaline$useConfiguredSpawnWait(int radius) {
        return this.adrenaline$spawnPreparation == null ? radius : this.adrenaline$spawnPreparation.progressRadius();
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"))
    @ControlsOptimization(Optimization.SPAWN_WAIT)
    private int adrenaline$waitForResolvedSpawn(int generated) {
        return this.adrenaline$spawnSearch.isPending() ? -1 : generated;
    }

    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V"), cancellable = true)
    private void adrenaline$advanceSpawnSearch(ChunkProgressListener listener, CallbackInfo ci) {
        if (!((MinecraftServer) (Object) this).isRunning()) {
            this.adrenaline$spawnSearch.reset();
            listener.stop();
            ci.cancel();
            return;
        }
        this.adrenaline$spawnSearch.advance(listener, this.adrenaline$spawnPreparation);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void adrenaline$beginIncrementalSpawnSaving(ChunkProgressListener listener, CallbackInfo ci) {
        this.adrenaline$lastSpawnSaveCount = 0;
    }

    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V", ordinal = 0))
    private void adrenaline$saveGeneratedSpawnChunks(ChunkProgressListener listener, CallbackInfo ci) {
        ServerChunkCache chunkSource = ((MinecraftServer) (Object) this).overworld().getChunkSource();
        int generated = chunkSource.getTickingGenerated();
        int interval = AdrenalineConfig.incrementalSaveInterval();
        if (interval > 0 && generated - this.adrenaline$lastSpawnSaveCount >= interval) {
            chunkSource.save(false);
            this.adrenaline$lastSpawnSaveCount = generated;
        }
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void adrenaline$finishIncrementalSpawnSaving(ChunkProgressListener listener, CallbackInfo ci) {
        if (this.adrenaline$initialWorldCreation && AdrenalineConfig.saveChunksAfterWorldCreation()) {
            ((MinecraftServer) (Object) this).overworld().getChunkSource().save(false);
        }
    }

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$startAdaptiveWorkers(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        this.adrenaline$initialWorldCreation = !server.getWorldData().overworldData().isInitialized();
        this.adrenaline$spawnSearch.reset();
        AdaptiveWorldgenWorkers.start(server);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$stopAdaptiveWorkers(CallbackInfo ci) {
        this.adrenaline$spawnSearch.reset();
        AdaptiveWorldgenWorkers.stop((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void adrenaline$beginAdaptiveWorkerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        AdaptiveWorldgenWorkers.beginTick((MinecraftServer) (Object) this);
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
}
