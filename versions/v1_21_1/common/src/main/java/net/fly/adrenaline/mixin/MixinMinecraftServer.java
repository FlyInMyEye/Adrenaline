package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BooleanSupplier;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.AdaptiveWorldgenWorkers;
import net.fly.adrenaline.util.SpawnPreparation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1100)
public abstract class MixinMinecraftServer {

    @Unique
    private SpawnPreparation adrenaline$spawnPreparation;

    @WrapOperation(method = "loadLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/progress/ChunkProgressListenerFactory;create(I)Lnet/minecraft/server/level/progress/ChunkProgressListener;"))
    @ControlsOptimization(Optimization.SPAWN_RADIUS)
    private ChunkProgressListener adrenaline$useConfiguredSpawnProgress(ChunkProgressListenerFactory factory, int radius, Operation<ChunkProgressListener> original) {
        if (!OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS) && !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            radius = AdrenalineConfig.resolvedSpawnZoneRadius() - 1;
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
        try (SpawnPreparation preparation = new SpawnPreparation(((MinecraftServer) (Object) this).overworld(), AdrenalineConfig.internalSpawnPreparationRadius())) {
            this.adrenaline$spawnPreparation = preparation;
            original.call(listener);
        } finally {
            this.adrenaline$spawnPreparation = null;
        }
    }

    @ModifyExpressionValue(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"))
    @ControlsOptimization(Optimization.SPAWN_WAIT)
    private int adrenaline$useConfiguredSpawnWait(int radius) {
        return this.adrenaline$spawnPreparation == null ? radius : this.adrenaline$spawnPreparation.progressRadius();
    }

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$startAdaptiveWorkers(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.start((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$stopAdaptiveWorkers(CallbackInfo ci) {
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
