package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 2000)
public abstract class MixinMinecraftServerWarmup {

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
