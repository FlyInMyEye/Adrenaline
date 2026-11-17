package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.WorldgenStatsOverlay;
import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.fly.adrenaline.client.ModernFixCompatController;
import net.fly.adrenaline.BuildConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftClientLifecycle {
    @Unique
    private final BackgroundWorldgenWarmup adrenaline$worldgenWarmup = new BackgroundWorldgenWarmup();

    @Unique
    private final ModernFixCompatController adrenaline$modernFixController = new ModernFixCompatController();

    @Inject(method = "tick", at = @At("RETURN"))
    private void adrenaline$tickClient(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        this.adrenaline$modernFixController.tick(minecraft);
        this.adrenaline$worldgenWarmup.tick(minecraft);
        if (BuildConfig.DEBUG) {
            WorldgenStatsOverlay.tick(minecraft);
        }
    }
}
