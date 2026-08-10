package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.util.WorldgenBenchmarkHooks;
import net.fly.adrenaline.util.WorldgenDifferenceState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServerWorldgenDebug {

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$clearWorldgenDifferenceState(CallbackInfo ci) {
        if (BuildConfig.DEBUG) {
            WorldgenDifferenceState.deactivate((MinecraftServer) (Object) this);
        }
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void adrenaline$runWorldgenBenchmark(ChunkProgressListener progressListener, CallbackInfo ci) {
        if (BuildConfig.DEBUG) {
            WorldgenBenchmarkHooks.onServerPrepared((MinecraftServer) (Object) this);
        }
    }
}
