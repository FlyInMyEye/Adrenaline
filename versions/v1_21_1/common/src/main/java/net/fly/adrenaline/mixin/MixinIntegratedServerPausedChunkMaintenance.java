package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServerPausedChunkMaintenance {
    @Inject(
        method = "tickServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;tickPaused()V", shift = At.Shift.AFTER)
    )
    private void adrenaline$maintainChunkStorage(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        for (ServerLevel level : ((MinecraftServer) (Object) this).getAllLevels()) {
            ((MixinChunkMapMaintenanceInvoker) level.getChunkSource().chunkMap)
                .adrenaline$tickStorage(shouldKeepTicking);
        }
    }
}
