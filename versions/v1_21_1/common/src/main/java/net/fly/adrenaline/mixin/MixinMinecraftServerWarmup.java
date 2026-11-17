package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.fly.adrenaline.util.SpawnPreparation;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.minecraft.Util;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 2000)
public abstract class MixinMinecraftServerWarmup {

    @Shadow private long nextTickTimeNanos;
    @Shadow protected abstract void waitUntilNextTick();

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void adrenaline$finishWarmupPreparation(ChunkProgressListener listener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!BackgroundWorldgenWarmupState.isServer(server)
            || !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_RADIUS) && !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_WAIT)) {
            return;
        }
        int radius = BackgroundWorldgenWarmupState.spawnZoneRadius() + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1;
        int diameter = radius * 2 - 1;
        try (SpawnPreparation preparation = new SpawnPreparation(server.overworld(), radius)) {
            while (server.isRunning() && BackgroundWorldgenWarmupState.isServer(server)
                && server.overworld().getChunkSource().getTickingGenerated() < diameter * diameter) {
                this.nextTickTimeNanos = Util.getNanos() + 10_000_000L;
                this.waitUntilNextTick();
            }
        }
    }

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void adrenaline$onlyMaintainWarmupChunks(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!WarmupWorldDiscard.isServer(server)) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            level.getChunkSource().tick(shouldKeepTicking, false);
        }
        ci.cancel();
    }
}
