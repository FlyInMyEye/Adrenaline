package net.fly.adrenaline.mixin;

import java.util.stream.Stream;
import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class MixinServerLevelWarmup {

    @Inject(method = {"addLegacyChunkEntities", "addWorldGenChunkEntities"}, at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipWarmupChunkEntities(Stream<Entity> entities, CallbackInfo ci) {
        if (this.adrenaline$isWarmupLevel()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipWarmupEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (this.adrenaline$isWarmupLevel()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipWarmupPlayer(ServerPlayer player, CallbackInfo ci) {
        if (this.adrenaline$isWarmupLevel()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean adrenaline$isWarmupLevel() {
        return WarmupWorldDiscard.isServer(((ServerLevel) (Object) this).getServer());
    }
}
