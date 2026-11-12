package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServerLifecycle {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void adrenaline$updatePriorityFoci(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (AdrenalineConfig.parallelWorldgenEnabled()) {
            ChunkJob.updatePriorityFoci((MinecraftServer) (Object) this);
        }
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void adrenaline$clearPriorityFoci(CallbackInfo ci) {
        ChunkJobScheduler.clearServer((MinecraftServer) (Object) this);
    }

}
