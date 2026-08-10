package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.util.WorldgenBenchmarkHooks;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoggerChunkProgressListener.class)
public class MixinLoggerChunkProgressListener {

    @Inject(method = "updateSpawnPos", at = @At("HEAD"))
    private void adrenaline$startWorldgenBenchmarkGeneration(ChunkPos spawnPos, CallbackInfo ci) {
        if (BuildConfig.DEBUG) {
            WorldgenBenchmarkHooks.onSpawnGenerationStarted();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void adrenaline$finishWorldgenBenchmarkGeneration(CallbackInfo ci) {
        if (BuildConfig.DEBUG) {
            WorldgenBenchmarkHooks.onSpawnGenerationFinished();
        }
    }
}
