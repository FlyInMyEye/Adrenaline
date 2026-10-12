package net.fly.adrenaline.mixin;

import net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 998)
public abstract class MixinServerPlayInitialization {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void adrenaline$awaitNetworkingInitialization(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (((NetworkHandlerExtensions) this).getAddon() == null) {
            PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, this.server);
        }
    }
}
