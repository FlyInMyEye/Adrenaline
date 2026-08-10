package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.client.WorldgenDifferenceHud;
import net.fly.adrenaline.util.WorldgenDifferenceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinWorldgenDifferenceInput {

    @Unique
    private boolean adrenaline$worldgenDifferenceUseHeld;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void adrenaline$cycleWorldgenDifference(CallbackInfo ci) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player == null) {
            return;
        }

        InteractionHand hand;
        if (WorldgenDifferenceState.isTool(minecraft.player.getMainHandItem())) {
            hand = InteractionHand.MAIN_HAND;
        } else if (WorldgenDifferenceState.isTool(minecraft.player.getOffhandItem())) {
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }

        if (this.adrenaline$worldgenDifferenceUseHeld) {
            ci.cancel();
            return;
        }
        this.adrenaline$worldgenDifferenceUseHeld = true;

        IntegratedServer integratedServer = minecraft.getSingleplayerServer();
        BlockPos pos = WorldgenDifferenceHud.targetedDifference(minecraft);
        if (pos == null) {
            return;
        }
        integratedServer.execute(() -> WorldgenDifferenceState.cycle(integratedServer.overworld(), pos));
        minecraft.player.swing(hand);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void adrenaline$releaseWorldgenDifferenceUse(CallbackInfo ci) {
        if (BuildConfig.DEBUG && !((Minecraft) (Object) this).options.keyUse.isDown()) {
            this.adrenaline$worldgenDifferenceUseHeld = false;
        }
    }
}
